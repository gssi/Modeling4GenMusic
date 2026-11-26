package org.modeling4genmusic.studio.assistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.derivation.DerivationProfile;
import org.modeling4genmusic.studio.derivation.DerivationService;
import org.modeling4genmusic.studio.derivation.TransformationScript;
import org.modeling4genmusic.studio.generation.ExerciseGenerator.Availability;
import org.modeling4genmusic.studio.generation.OllamaClient;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;
import org.modeling4genmusic.studio.validation.EvlValidator;
import org.modeling4genmusic.studio.validation.RuleCatalog;
import org.modeling4genmusic.studio.validation.RuleSet;
import org.modeling4genmusic.studio.validation.ValidationProfile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Turns the description of a rule into EVL or ETL with a language model, and tells whether the script
 * with the new rule would pass the checks that guard saving: syntax, then a trial run on the workspace
 * models. When it would not, the model is asked again with the errors. Nothing is saved here: the
 * proposal goes back to the editor, where a person reads it and decides.
 */
@Service
public class RuleAssistant {

    private static final int MAX_TOKENS = 1200;
    private static final int EXAMPLES = 3;

    private final AssistantProperties properties;
    private final OllamaClient ollama;
    private final MetamodelRegistry registry;
    private final EvlValidator validator;
    private final RuleCatalog catalog;
    private final DerivationService derivation;

    public RuleAssistant(AssistantProperties properties, OllamaClient ollama, MetamodelRegistry registry,
            EvlValidator validator, RuleCatalog catalog, DerivationService derivation) {
        this.properties = properties;
        this.ollama = ollama;
        this.registry = registry;
        this.validator = validator;
        this.catalog = catalog;
        this.derivation = derivation;
    }

    /**
     * @param rule     the code proposed for the new rule
     * @param source   the script with the rule added at the end
     * @param line     line of {@code source} where the added part starts
     * @param problems syntax errors of {@code source}
     * @param failures models on which a trial run of {@code source} stopped with an error
     * @param attempts how many times the model was asked
     * @param effects  for a validation rule that passes the checks, what it reports on the workspace models;
     *                 null otherwise
     */
    public record Proposal(String rule, String source, int line, List<RuleSet.Problem> problems,
            List<RuleSet.Failure> failures, int attempts, double seconds, String model, RuleCatalog.Effects effects) {

        boolean sound() {
            return problems.isEmpty() && failures.isEmpty();
        }
    }

    public Availability availability() {
        return ollama.availability(properties.model());
    }

    /**
     * @param script      name of the script the rule is for
     * @param source      current text of the script, as it is in the editor
     * @param description what the rule must do
     */
    public Proposal propose(String workspaceId, String script, String source, String description) {
        if (description == null || description.isBlank()) {
            throw ApiException.badRequest("Descrivi la regola da scrivere");
        }
        if (source == null) {
            throw ApiException.badRequest("Manca il testo dello script");
        }
        ScriptContext context = context(script, source);
        Availability availability = availability();
        if (!availability.available()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, availability.reason());
        }

        long start = System.nanoTime();
        List<RulePrompt.Attempt> previous = new ArrayList<>();
        Proposal best = null;
        int attempts = Math.max(1, properties.maxAttempts());
        int asked = 0;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            asked = attempt;
            String answer = ollama.chat(properties.model(), RulePrompt.build(context, registry, description, previous),
                    properties.temperature(), properties.timeout(), MAX_TOKENS);
            Optional<String> rule = RuleExtractor.extract(answer, context.language());
            if (rule.isEmpty()) {
                previous.add(new RulePrompt.Attempt(answer, List.of("The answer holds no rule.")));
                continue;
            }
            Proposal proposal = check(workspaceId, context, source, description, rule.get(), attempt);
            if (best == null || errors(proposal) < errors(best)) {
                best = proposal;
            }
            if (proposal.sound()) {
                break;
            }
            previous.add(new RulePrompt.Attempt(answer, describe(proposal)));
        }
        if (best == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Il modello non ha prodotto una regola. Prova a descriverla in modo più preciso.");
        }
        double seconds = Math.round((System.nanoTime() - start) / 1e8) / 10.0;
        return new Proposal(best.rule(), best.source(), best.line(), best.problems(), best.failures(),
                asked, seconds, properties.model(), best.effects());
    }

    private static int errors(Proposal proposal) {
        return proposal.problems().size() * 10 + proposal.failures().size();
    }

    /** Adds the rule at the end of the script and runs the checks that guard saving. */
    private Proposal check(String workspaceId, ScriptContext context, String source, String description, String rule,
            int attempt) {
        String base = source.stripTrailing();
        int line = (int) base.lines().count() + 2;
        String comment = description.strip().lines().map(l -> "// " + l.strip()).reduce((a, b) -> a + "\n" + b)
                .orElse("");
        String merged = base + "\n\n" + comment + "\n" + rule + "\n";

        List<RuleSet.Problem> problems;
        List<RuleSet.Failure> failures;
        List<String> newNames;
        RuleCatalog.Effects effects = null;
        if (context.language() == RuleLanguage.EVL) {
            RuleSet checked = catalog.check(workspaceId, context.script(), merged);
            problems = new ArrayList<>(checked.problems());
            failures = checked.failures();
            newNames = checked.contexts().stream().flatMap(c -> c.rules().stream()).map(RuleSet.Rule::name).toList();
            if (problems.isEmpty() && failures.isEmpty()) {
                List<String> added = newNames.stream().filter(name -> !context.names().contains(name)).toList();
                effects = catalog.effects(workspaceId, context.script(), merged, added);
            }
        } else {
            TransformationScript checked = derivation.check(workspaceId, merged);
            problems = new ArrayList<>(checked.problems());
            failures = checked.failures();
            newNames = new ArrayList<>(checked.rules().stream().map(TransformationScript.Rule::name).toList());
            checked.blocks().stream().map(TransformationScript.Block::name).filter(n -> n != null)
                    .forEach(newNames::add);
            // A rule that creates elements in a model the script only reads would change the inputs.
            checked.rules().stream().filter(r -> !context.names().contains(r.name()))
                    .forEach(r -> r.to().stream().map(type -> type.substring(0, Math.max(0, type.indexOf('!'))))
                            .filter(context.readOnly()::contains).distinct()
                            .forEach(alias -> problems.add(new RuleSet.Problem(r.line(), 0, "Rule " + r.name()
                                    + " creates elements in " + alias + ", which is read only: create them in "
                                    + String.join(" or ", context.models().keySet().stream()
                                            .filter(a -> !context.readOnly().contains(a)).toList())))));
        }
        if (problems.isEmpty()) {
            newNames.stream().filter(name -> newNames.indexOf(name) != newNames.lastIndexOf(name)).distinct()
                    .forEach(name -> problems.add(new RuleSet.Problem(line, 0,
                            "The name " + name + " is already used by another rule of the script")));
        }
        return new Proposal(rule, merged, line, problems, failures, attempt, 0, properties.model(), effects);
    }

    /** The errors of a proposal as sentences for the model, with lines counted from the start of its rule. */
    private static List<String> describe(Proposal proposal) {
        List<String> errors = new ArrayList<>();
        // The rule starts one line after the comment that precedes it.
        int commentLines = (int) proposal.source().lines().skip(proposal.line() - 1L)
                .takeWhile(l -> l.startsWith("//")).count();
        int first = proposal.line() + commentLines;
        for (RuleSet.Problem problem : proposal.problems()) {
            errors.add("Syntax error" + (problem.line() >= first ? " at line " + (problem.line() - first + 1)
                    + " of your rule" : "") + ": " + problem.reason());
        }
        proposal.failures().stream().map(RuleSet.Failure::message).distinct()
                .forEach(message -> errors.add("Error when the script runs on a model: " + message
                        + ". Line numbers refer to the whole script; your rule starts at line " + first + "."));
        return errors;
    }

    private ScriptContext context(String script, String source) {
        Optional<ValidationProfile> validation = validator.profiles().stream()
                .filter(p -> p.script().equals(script)).findFirst();
        if (validation.isPresent()) {
            ValidationProfile profile = validation.get();
            Map<String, String> models = new LinkedHashMap<>();
            models.put(profile.model(), profile.metamodel());
            if (profile.related() != null) {
                profile.related().forEach(r -> models.put(r.alias(), r.metamodel()));
            }
            RuleSet described = RuleCatalog.describe(profile, source);
            List<String> examples = described.contexts().stream()
                    .flatMap(c -> c.rules().stream().map(r -> "context " + c.type() + " {\n\n  "
                            + r.source().replace("\n", "\n  ") + "\n}"))
                    .sorted(Comparator.comparingInt(String::length)).limit(EXAMPLES).toList();
            List<String> names = described.contexts().stream().flatMap(c -> c.rules().stream())
                    .map(RuleSet.Rule::name).toList();
            return new ScriptContext(script, RuleLanguage.EVL, models, List.copyOf(models.keySet()),
                    profile.tools() == null ? Map.of() : profile.tools(), signatures(described.operations()),
                    examples, names);
        }
        DerivationProfile profile = derivation.profile();
        if (!profile.script().equals(script)) {
            throw ApiException.notFound("Unknown script: " + script);
        }
        Map<String, String> models = new LinkedHashMap<>();
        profile.inputs().forEach(i -> models.put(i.alias(), i.metamodel()));
        profile.outputs().forEach(o -> models.put(o.alias(), o.metamodel()));
        TransformationScript described = DerivationService.describe(profile, source);
        List<String> examples = new ArrayList<>(described.rules().stream()
                .map(r -> (r.comment() == null ? "" : "// " + r.comment() + "\n") + r.source())
                .sorted(Comparator.comparingInt(String::length)).limit(EXAMPLES - 1L).toList());
        described.blocks().stream().filter(b -> "post".equals(b.kind())).map(TransformationScript.Block::source)
                .min(Comparator.comparingInt(String::length)).ifPresent(examples::add);
        List<String> names = new ArrayList<>(described.rules().stream().map(TransformationScript.Rule::name).toList());
        described.blocks().stream().map(TransformationScript.Block::name).filter(n -> n != null).forEach(names::add);
        return new ScriptContext(script, RuleLanguage.ETL, models,
                profile.inputs().stream().map(DerivationProfile.Input::alias).toList(),
                profile.tools() == null ? Map.of() : profile.tools(), signatures(described.operations()), examples,
                names);
    }

    /** First line of each operation, with its comment: enough to call it. */
    private static List<String> signatures(List<RuleSet.Operation> operations) {
        return operations.stream().map(op -> {
            String first = op.source().lines().findFirst().orElse(op.name());
            int brace = first.indexOf('{');
            String signature = (brace > 0 ? first.substring(0, brace) : first).strip();
            return signature + (op.comment() == null ? "" : "  // " + op.comment());
        }).toList();
    }
}
