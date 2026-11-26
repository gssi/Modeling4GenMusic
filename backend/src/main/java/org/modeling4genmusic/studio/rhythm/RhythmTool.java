package org.modeling4genmusic.studio.rhythm;

/**
 * Rhythm checks as seen from the validation scripts, where it is available as the {@code rhythm} variable.
 * Parameters are untyped because an unset attribute reaches a script call as null.
 */
public class RhythmTool {

    public boolean isValid(Object expression) {
        return RhythmPattern.isValid(text(expression));
    }

    /** Reason why the expression is not a valid pattern, or null when it is valid. */
    public String errorOf(Object expression) {
        return RhythmPattern.errorOf(text(expression));
    }

    /** Canonical spelling, to compare two expressions regardless of spacing. */
    public String normalize(Object expression) {
        return RhythmPattern.normalize(text(expression));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
