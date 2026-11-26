package org.modeling4genmusic.studio.validation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A validation script as the EVL parser reads it: where it applies and the rules it holds, each with
 * the text of its parts. It is a description for people; nothing is executed to build it.
 *
 * @param script      file name in the {@code epsilon/} resource folder
 * @param metamodel   nsURI of the models the script validates
 * @param exceptRoles model roles the script is not run on
 * @param model       name of the validated model inside the script
 * @param related     other models the script reads
 * @param tools       names of the helper objects the script can call
 * @param source      the whole script
 * @param problems    syntax errors; when there are any, {@code contexts} and {@code operations} are empty
 * @param modified    true when the script was edited and differs from the one shipped
 * @param failures    models on which a trial run of the script stopped with an error; filled only by a check
 */
public record RuleSet(String script, String metamodel, List<String> exceptRoles, String model,
        List<ValidationProfile.Related> related, List<String> tools, String source, List<Problem> problems,
        List<Context> contexts, List<Operation> operations, boolean modified, List<Failure> failures) {

    public record Failure(String modelId, String modelName, String message) {
    }

    public RuleSet with(boolean isModified, List<Failure> trialFailures) {
        return new RuleSet(script, metamodel, exceptRoles, model, related, tools, source, problems, contexts,
                operations, isModified, trialFailures);
    }

    public record Problem(int line, int column, String reason) {
    }

    /** The rules checked on every element of one type, for example {@code T!ExerciseSpec}. */
    public record Context(String type, List<Rule> rules) {
    }

    /**
     * @param comment the comment lines right above the rule, without the markers
     * @param line    first line of the rule in the script, starting from 1
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rule(String name, Diagnostic.Severity severity, String comment, String guard, String check,
            String message, List<Fix> fixes, String source, int line) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Fix(String title, String guard, String body) {
    }

    /** A helper operation declared in the script and used by its rules. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Operation(String name, String context, String comment, String source, int line) {
    }
}
