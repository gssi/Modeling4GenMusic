package org.modeling4genmusic.studio.derivation;

import java.util.List;

import org.modeling4genmusic.studio.validation.RuleSet;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The transformation script as the ETL parser reads it: the models it reads and writes and its rules,
 * each with its text. It is a description for people; nothing is executed to build it.
 *
 * @param inputs   models read, with the name each has inside the script
 * @param outputs  models written
 * @param tools    names of the helper objects the script can call
 * @param problems syntax errors; when there are any, the lists of rules, blocks and operations are empty
 * @param blocks   {@code pre} and {@code post} blocks, in the order they run
 * @param modified true when the script was edited and differs from the one shipped
 * @param failures inputs on which a trial run stopped with an error; filled only by a check
 */
public record TransformationScript(String script, List<Model> inputs, List<Model> outputs, List<String> tools,
        String source, List<RuleSet.Problem> problems, List<Rule> rules, List<Block> blocks,
        List<RuleSet.Operation> operations, boolean modified, List<RuleSet.Failure> failures) {

    public record Model(String alias, String metamodel) {
    }

    /**
     * A rule that creates target elements from a source element.
     *
     * @param from    type of the source element, for example {@code T!ContainsPattern}
     * @param to      types of the created elements
     * @param comment the comment lines right above the rule, without the markers
     * @param line    first line of the rule in the script, starting from 1
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rule(String name, String from, List<String> to, String comment, String guard, String source,
            int line) {
    }

    /** @param kind {@code pre} or {@code post} */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Block(String kind, String name, String comment, String source, int line) {
    }

    public TransformationScript with(boolean isModified, List<RuleSet.Failure> trialFailures) {
        return new TransformationScript(script, inputs, outputs, tools, source, problems, rules, blocks, operations,
                isModified, trialFailures);
    }
}
