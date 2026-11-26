package org.modeling4genmusic.studio.assistant;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Takes the code of a rule out of the answer of a language model. */
final class RuleExtractor {

    private static final Pattern FENCE = Pattern.compile("```[A-Za-z]*[ \\t]*\\R(.*?)```", Pattern.DOTALL);
    private static final Pattern EVL_RULE = Pattern.compile("(?m)^\\s*context\\b");
    private static final Pattern ETL_RULE = Pattern.compile("(?m)^\\s*(@\\w+\\s+)*(rule|pre|post)\\b");

    private RuleExtractor() {
    }

    /**
     * The code inside the first fenced block, or the whole answer when there is no fence; empty when
     * what is left does not hold a rule of the given language.
     */
    static Optional<String> extract(String answer, RuleLanguage language) {
        if (answer == null) {
            return Optional.empty();
        }
        Matcher fence = FENCE.matcher(answer);
        String code = (fence.find() ? fence.group(1) : answer.replace("```", "")).strip();
        Pattern expected = language == RuleLanguage.EVL ? EVL_RULE : ETL_RULE;
        return code.isEmpty() || !expected.matcher(code).find() ? Optional.empty() : Optional.of(code);
    }
}
