package org.modeling4genmusic.studio.validation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.evl.EvlModule;
import org.eclipse.epsilon.evl.dom.Constraint;
import org.eclipse.epsilon.evl.dom.ConstraintContext;
import org.eclipse.epsilon.evl.dom.Fix;
import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.epsilon.Scripts;
import org.modeling4genmusic.studio.epsilon.SourceText;
import org.modeling4genmusic.studio.model.ModelDocument;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Describes the validation scripts and guards their editing. Each script is parsed with the EVL parser, the same
 * that runs it, so what is shown is what the engine understands; a script that does not parse is
 * returned with its syntax errors instead of its rules.
 */
@Component
public class RuleCatalog {

    private final EvlValidator validator;
    private final RuleStore rules;
    private final WorkspaceStore store;

    public RuleCatalog(EvlValidator validator, RuleStore rules, WorkspaceStore store) {
        this.validator = validator;
        this.rules = rules;
        this.store = store;
    }

    public List<RuleSet> ruleSets() {
        return validator.profiles().stream().map(this::current).toList();
    }

    /**
     * Tells whether a new text of a script could be kept: it is parsed and, if the syntax is right, run
     * on the workspace models it applies to. Syntax cannot reveal a wrong property or operation name;
     * the trial run does, as far as the models at hand exercise the rules.
     */
    public RuleSet check(String workspaceId, String script, String source) {
        ValidationProfile profile = profile(script);
        RuleSet described = describe(profile, source == null ? "" : source);
        List<RuleSet.Failure> failures = new ArrayList<>();
        if (described.problems().isEmpty()) {
            for (ModelDocument model : store.list(workspaceId, profile.metamodel())) {
                if (!profile.appliesTo(model.metamodel(), model.role())) {
                    continue;
                }
                try {
                    validator.validateWith(profile, described.source(), model.id(), store.load(workspaceId, model.id()));
                } catch (RuntimeException e) {
                    failures.add(new RuleSet.Failure(model.id(), model.name(), Scripts.reason(e)));
                }
            }
        }
        return described.with(rules.isModified(script), failures);
    }

    /** What a rule reports on one element of a workspace model. */
    public record Report(String modelName, String rule, String elementId, String message) {
    }

    /**
     * What the given rules of a script report on the workspace models the script applies to, and on how
     * many models they were run. It shows what a new rule does before it is kept.
     */
    public Effects effects(String workspaceId, String script, String source, List<String> ruleNames) {
        ValidationProfile profile = profile(script);
        List<Report> reports = new ArrayList<>();
        int models = 0;
        for (ModelDocument model : store.list(workspaceId, profile.metamodel())) {
            if (!profile.appliesTo(model.metamodel(), model.role())) {
                continue;
            }
            models++;
            try {
                validator.validateWith(profile, source, model.id(), store.load(workspaceId, model.id())).stream()
                        .map(EvlValidator.Finding::diagnostic).filter(d -> ruleNames.contains(d.constraint()))
                        .forEach(d -> reports.add(new Report(model.name(), d.constraint(), d.elementId(), d.message())));
            } catch (RuntimeException e) {
                // A script that fails is reported by check; here only what it says when it runs.
            }
        }
        return new Effects(models, reports);
    }

    /** @param models how many models the rules were run on */
    public record Effects(int models, List<Report> reports) {
    }

    /** Keeps a new text of a script, provided it passes {@link #check}. */
    public synchronized RuleSet save(String workspaceId, String script, String source) {
        RuleSet checked = check(workspaceId, script, source);
        if (!checked.problems().isEmpty()) {
            RuleSet.Problem first = checked.problems().get(0);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "The script has syntax errors and was not saved (line "
                    + first.line() + ": " + first.reason() + ")");
        }
        if (!checked.failures().isEmpty()) {
            RuleSet.Failure first = checked.failures().get(0);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "The script fails on model '" + first.modelName()
                    + "' and was not saved: " + first.message());
        }
        if (checked.source().equals(Scripts.read(script))) {
            rules.reset(script);
        } else {
            rules.save(script, checked.source());
        }
        return current(profile(script));
    }

    /** Drops the edited text of a script and goes back to the one shipped. */
    public synchronized RuleSet reset(String script) {
        ValidationProfile profile = profile(script);
        rules.reset(script);
        return current(profile);
    }

    private RuleSet current(ValidationProfile profile) {
        return describe(profile, rules.source(profile.script())).with(rules.isModified(profile.script()), List.of());
    }

    private ValidationProfile profile(String script) {
        return validator.profiles().stream().filter(p -> p.script().equals(script)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Unknown validation script: " + script));
    }

    public static RuleSet describe(ValidationProfile profile, String source) {
        List<RuleSet.Problem> problems = new ArrayList<>();
        List<RuleSet.Context> contexts = new ArrayList<>();
        List<RuleSet.Operation> operations = new ArrayList<>();
        EvlModule module = new EvlModule();
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
            for (ConstraintContext context : module.getConstraintContexts()) {
                List<RuleSet.Rule> rules = context.getConstraints().stream().map(c -> rule(c, text)).toList();
                contexts.add(new RuleSet.Context(context.getTypeName(), rules));
            }
            for (org.eclipse.epsilon.eol.dom.Operation operation : module.getDeclaredOperations()) {
                operations.add(new RuleSet.Operation(operation.getName(),
                        text.of(operation.getContextTypeExpression()), text.commentAbove(operation),
                        text.of(operation), text.line(operation)));
            }
        }
        return new RuleSet(profile.script(), profile.metamodel(),
                profile.exceptRoles() == null ? List.of() : profile.exceptRoles(), profile.model(),
                profile.related() == null ? List.of() : profile.related(),
                profile.tools() == null ? List.of() : profile.tools().keySet().stream().sorted().toList(), source,
                problems, contexts, operations, false, List.of());
    }

    private static RuleSet.Rule rule(Constraint constraint, SourceText text) {
        Diagnostic.Severity severity = constraint.isInfo() ? Diagnostic.Severity.INFO
                : constraint.isCritique() ? Diagnostic.Severity.WARNING : Diagnostic.Severity.ERROR;
        List<RuleSet.Fix> fixes = new ArrayList<>();
        for (Fix fix : constraint.getFixes()) {
            fixes.add(new RuleSet.Fix(text.part(fix.getTitleBlock(), "title"), text.part(fix.getGuardBlock(), "guard"),
                    text.part(fix.getBodyBlock(), "do")));
        }
        return new RuleSet.Rule(constraint.getName(), severity, text.commentAbove(constraint),
                text.part(constraint.getGuardBlock(), "guard"), text.part(constraint.getCheckBlock(), "check"),
                text.part(constraint.getMessageBlock(), "message"), fixes, text.of(constraint), text.line(constraint));
    }
}
