package org.modeling4genmusic.studio.derivation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.eol.dom.Parameter;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.EolUserException;
import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.etl.dom.TransformationRule;
import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.epsilon.Scripts;
import org.modeling4genmusic.studio.epsilon.SourceText;
import org.modeling4genmusic.studio.model.ModelDocument;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;
import org.modeling4genmusic.studio.validation.Diagnostic;
import org.modeling4genmusic.studio.validation.RuleSet;
import org.modeling4genmusic.studio.validation.RuleStore;
import org.modeling4genmusic.studio.validation.ValidationService;
import org.modeling4genmusic.studio.workspace.ModelMeta;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs the ETL transformation described by {@code epsilon/derivation.json} on models of the workspace
 * and saves what it produces. Which models go in and out, and under which names, comes from the descriptor;
 * the text of the script comes from the {@link RuleStore}, where an edited one replaces the original.
 */
@Service
public class DerivationService {

    private final WorkspaceStore store;
    private final MetamodelRegistry registry;
    private final ValidationService validation;
    private final DerivationProfile profile;
    private final RuleStore scripts;

    public DerivationService(WorkspaceStore store, MetamodelRegistry registry, ValidationService validation,
            ObjectMapper json, RuleStore scripts) {
        this.scripts = scripts;
        this.store = store;
        this.registry = registry;
        this.validation = validation;
        try (InputStream in = Scripts.open("derivation.json")) {
            this.profile = json.readValue(in, DerivationProfile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the derivation descriptor", e);
        }
    }

    /** Ids of the produced models by response field, plus the diagnostics worth showing. */
    public record Result(Map<String, String> outputs, List<Diagnostic> diagnostics) {
    }

    /**
     * @param parameters request fields; each input of the descriptor reads its model id from one of them
     */
    public synchronized Result derive(String workspaceId, Map<String, String> parameters) {
        Map<DerivationProfile.Input, String> inputIds = new LinkedHashMap<>();
        for (DerivationProfile.Input input : profile.inputs()) {
            String id = parameters == null ? null : parameters.get(input.parameter());
            if (id == null || id.isBlank()) {
                throw ApiException.badRequest("Manca il campo " + input.parameter());
            }
            ModelMeta meta = store.meta(workspaceId, id);
            if (!input.metamodel().equals(meta.metamodel())) {
                throw ApiException.badRequest("Il modello «" + meta.name() + "» non è del tipo atteso per "
                        + input.parameter());
            }
            inputIds.put(input, id);
        }

        List<Diagnostic> residual = new ArrayList<>();
        for (var entry : inputIds.entrySet()) {
            if (!entry.getKey().validated()) {
                continue;
            }
            List<Diagnostic> diagnostics = validation.validate(workspaceId, entry.getValue());
            long errors = diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).count();
            if (errors > 0) {
                String name = store.meta(workspaceId, entry.getValue()).name();
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Il modello «" + name + "» ha "
                        + (errors == 1 ? "un errore" : errors + " errori") + ": va corretto prima di derivare.");
            }
            if (entry.getKey().primary()) {
                residual.addAll(diagnostics);
            }
        }

        String primaryId = inputIds.get(profile.primary());
        String primaryName = store.meta(workspaceId, primaryId).name();

        Map<DerivationProfile.Output, XMLResource> produced;
        Map<String, String> outputIds = outputIds(primaryId);
        try {
            produced = run(workspaceId, inputIds, scripts.source(profile.script()));
        } catch (EolRuntimeException e) {
            // A script stops with `throw` when its inputs cannot be transformed: the reason is for the user.
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Derivazione non riuscita: " + e.getReason());
        }

        produced.forEach((output, resource) -> store.put(workspaceId, outputIds.get(output.result()), resource,
                output.name().formatted(primaryName), output.metamodel(), output.role()));

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (DerivationProfile.Output output : profile.outputs()) {
            if (output.validated()) {
                diagnostics.addAll(validation.validate(workspaceId, outputIds.get(output.result())));
            }
        }
        diagnostics.addAll(residual);
        return new Result(outputIds, diagnostics);
    }

    private Map<String, String> outputIds(String primaryId) {
        Map<String, String> ids = new LinkedHashMap<>();
        profile.outputs().forEach(output -> ids.put(output.result(), primaryId + "." + output.idSuffix()));
        return ids;
    }

    /** Runs the given text of the script on the inputs and returns what it builds, without saving anything. */
    private Map<DerivationProfile.Output, XMLResource> run(String workspaceId,
            Map<DerivationProfile.Input, String> inputIds, String source) throws EolRuntimeException {
        ResourceSet session = store.newSession();
        EtlModule module = new EtlModule();
        Scripts.parse(module, profile.script(), source);
        inputIds.forEach((input, id) -> module.getContext().getModelRepository()
                .addModel(Scripts.model(input.alias(), store.loadIn(session, workspaceId, id), registry.packages())));
        String primaryId = inputIds.get(profile.primary());
        Map<DerivationProfile.Output, XMLResource> produced = new LinkedHashMap<>();
        for (DerivationProfile.Output output : profile.outputs()) {
            XMLResource resource = store.createIn(session, workspaceId, primaryId + "." + output.idSuffix());
            produced.put(output, resource);
            module.getContext().getModelRepository()
                    .addModel(Scripts.model(output.alias(), resource, registry.packages()));
        }
        Map<String, Object> tools = Scripts.publishTools(module, profile.tools());
        module.execute();
        for (Object tool : tools.values()) {
            if (tool instanceof DerivationHook hook) {
                hook.afterDerivation();
            }
        }
        return produced;
    }

    // ---------------------------------------------------------------- the script itself

    /** The descriptor of the transformation: script, models read and written, tools. */
    public DerivationProfile profile() {
        return profile;
    }

    public TransformationScript script() {
        return describe(profile, scripts.source(profile.script())).with(scripts.isModified(profile.script()),
                List.of());
    }

    /**
     * Tells whether a new text of the script could be kept: it is parsed and, if the syntax is right, run
     * on the workspace models without saving what it produces. Syntax cannot reveal a wrong property or
     * operation name; the trial run does, as far as the models at hand exercise the rules.
     */
    public synchronized TransformationScript check(String workspaceId, String source) {
        TransformationScript described = describe(profile, source == null ? "" : source);
        List<RuleSet.Failure> failures = new ArrayList<>();
        if (described.problems().isEmpty()) {
            for (Map<DerivationProfile.Input, String> inputIds : trialInputs(workspaceId)) {
                String primaryId = inputIds.get(profile.primary());
                try {
                    run(workspaceId, inputIds, described.source());
                } catch (EolUserException e) {
                    // The script itself refused these inputs with `throw`: it works as written.
                } catch (EolRuntimeException | RuntimeException e) {
                    failures.add(new RuleSet.Failure(primaryId, store.meta(workspaceId, primaryId).name(),
                            Scripts.reason(e)));
                }
            }
        }
        return described.with(scripts.isModified(profile.script()), failures);
    }

    /** Keeps a new text of the script, provided it passes {@link #check}. */
    public synchronized TransformationScript save(String workspaceId, String source) {
        TransformationScript checked = check(workspaceId, source);
        if (!checked.problems().isEmpty()) {
            RuleSet.Problem first = checked.problems().get(0);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The script has syntax errors and was not saved (line " + first.line() + ": " + first.reason()
                            + ")");
        }
        if (!checked.failures().isEmpty()) {
            RuleSet.Failure first = checked.failures().get(0);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "The script fails on model '"
                    + first.modelName() + "' and was not saved: " + first.message());
        }
        if (checked.source().equals(Scripts.read(profile.script()))) {
            scripts.reset(profile.script());
        } else {
            scripts.save(profile.script(), checked.source());
        }
        return script();
    }

    /** Drops the edited text and goes back to the script shipped with the application. */
    public synchronized TransformationScript reset() {
        scripts.reset(profile.script());
        return script();
    }

    /**
     * Inputs to try a script on: every model that can be the primary input and is not itself a product of
     * the derivation, with, for each other input, the model it refers to or else the first one available.
     */
    private List<Map<DerivationProfile.Input, String>> trialInputs(String workspaceId) {
        DerivationProfile.Input primary = profile.primary();
        List<Map<DerivationProfile.Input, String>> result = new ArrayList<>();
        for (ModelDocument candidate : store.list(workspaceId, primary.metamodel())) {
            boolean product = profile.outputs().stream().anyMatch(o -> o.metamodel().equals(primary.metamodel())
                    && candidate.id().endsWith("." + o.idSuffix()));
            if (product) {
                continue;
            }
            Set<String> referenced = new HashSet<>();
            try {
                XMLResource resource = store.load(workspaceId, candidate.id());
                resource.getAllContents().forEachRemaining(element -> element.eCrossReferences().forEach(target -> {
                    if (target.eResource() != null && target.eResource() != resource) {
                        referenced.add(target.eResource().getURI().trimFileExtension().lastSegment());
                    }
                }));
            } catch (RuntimeException e) {
                continue;
            }
            Map<DerivationProfile.Input, String> inputIds = new LinkedHashMap<>();
            for (DerivationProfile.Input input : profile.inputs()) {
                if (input == primary) {
                    inputIds.put(input, candidate.id());
                    continue;
                }
                List<ModelDocument> models = store.list(workspaceId, input.metamodel());
                models.stream().filter(m -> referenced.contains(m.id())).findFirst()
                        .or(() -> models.stream().findFirst()).ifPresent(m -> inputIds.put(input, m.id()));
            }
            if (inputIds.size() == profile.inputs().size()) {
                result.add(inputIds);
            }
        }
        return result;
    }

    public static TransformationScript describe(DerivationProfile profile, String source) {
        List<RuleSet.Problem> problems = new ArrayList<>();
        List<TransformationScript.Rule> rules = new ArrayList<>();
        List<TransformationScript.Block> blocks = new ArrayList<>();
        List<RuleSet.Operation> operations = new ArrayList<>();
        EtlModule module = new EtlModule();
        try {
            module.parse(source);
            for (ParseProblem problem : module.getParseProblems()) {
                problems.add(new RuleSet.Problem(problem.getLine(), problem.getColumn(), problem.getReason()));
            }
        } catch (Exception e) {
            problems.add(new RuleSet.Problem(0, 0, String.valueOf(e.getMessage())));
        }
        if (problems.isEmpty()) {
            SourceText text = new SourceText(source);
            module.getDeclaredPre().forEach(pre -> blocks.add(new TransformationScript.Block("pre",
                    pre.getName(), text.commentAbove(pre), text.of(pre), text.line(pre))));
            for (TransformationRule rule : module.getDeclaredTransformationRules()) {
                rules.add(new TransformationScript.Rule(rule.getName(), rule.getSourceParameter().getTypeName(),
                        rule.getTargetParameters().stream().map(Parameter::getTypeName).toList(),
                        text.commentAbove(rule), text.part(rule.getGuard(), "guard"), text.of(rule),
                        text.line(rule)));
            }
            module.getDeclaredPost().forEach(post -> blocks.add(new TransformationScript.Block("post",
                    post.getName(), text.commentAbove(post), text.of(post), text.line(post))));
            for (org.eclipse.epsilon.eol.dom.Operation operation : module.getDeclaredOperations()) {
                operations.add(new RuleSet.Operation(operation.getName(),
                        text.of(operation.getContextTypeExpression()), text.commentAbove(operation),
                        text.of(operation), text.line(operation)));
            }
        }
        return new TransformationScript(profile.script(),
                profile.inputs().stream().map(i -> new TransformationScript.Model(i.alias(), i.metamodel())).toList(),
                profile.outputs().stream().map(o -> new TransformationScript.Model(o.alias(), o.metamodel())).toList(),
                profile.tools() == null ? List.of() : profile.tools().keySet().stream().sorted().toList(), source,
                problems, rules, blocks, operations, false, List.of());
    }
}
