package org.modeling4genmusic.studio.interval;

/**
 * Interval patterns as seen from the Epsilon scripts, where it is available as the {@code intervals}
 * variable. Parameters are untyped because an unset attribute reaches a script call as null.
 */
public class IntervalTool {

    public boolean isValid(Object expression) {
        return IntervalPattern.isValid(text(expression));
    }

    /** Reason why the expression is not a valid pattern, or null when it is valid. */
    public String errorOf(Object expression) {
        return IntervalPattern.errorOf(text(expression));
    }

    /** Widest step of a valid pattern in semitones, 0 for an invalid one. */
    public int maxLeap(Object expression) {
        String text = text(expression);
        return IntervalPattern.isValid(text) ? IntervalPattern.parse(text).maxLeap() : 0;
    }

    /** Name of an interval of that many semitones. */
    public String nameOf(Object semitones) {
        return IntervalPattern.nameOf(semitones instanceof Number n ? n.intValue() : 0);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
