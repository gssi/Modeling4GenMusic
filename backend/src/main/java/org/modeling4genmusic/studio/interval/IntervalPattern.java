package org.modeling4genmusic.studio.interval;

import java.util.ArrayList;
import java.util.List;

/**
 * Melodic pattern written as a sequence of steps in semitones: {@code +3 -2 +12} goes up a minor
 * third, down a major second and up an octave. Every step carries its sign; {@code 0} repeats the note.
 * The same grammar is implemented by the frontend parser.
 */
public final class IntervalPattern {

    /** Widest step that can be written: two octaves. */
    public static final int MAX_STEP = 24;

    private static final String[] NAMES = { "unisono", "seconda minore", "seconda maggiore", "terza minore",
            "terza maggiore", "quarta giusta", "tritono", "quinta giusta", "sesta minore", "sesta maggiore",
            "settima minore", "settima maggiore", "ottava", "nona minore", "nona maggiore", "decima minore",
            "decima maggiore" };

    private final List<Integer> steps;

    private IntervalPattern(List<Integer> steps) {
        this.steps = List.copyOf(steps);
    }

    public List<Integer> steps() {
        return steps;
    }

    /** Widest step, whatever its direction. */
    public int maxLeap() {
        return steps.stream().mapToInt(Math::abs).max().orElse(0);
    }

    /** Canonical text: steps separated by one space. */
    public String normalized() {
        return String.join(" ", steps.stream().map(s -> s > 0 ? "+" + s : String.valueOf(s)).toList());
    }

    /** Parses an expression or throws {@link IllegalArgumentException} with the reason. */
    public static IntervalPattern parse(String expression) {
        String error = errorOf(expression);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        List<Integer> steps = new ArrayList<>();
        for (String text : expression.strip().split("\\s+")) {
            steps.add(Integer.parseInt(text.startsWith("+") ? text.substring(1) : text));
        }
        return new IntervalPattern(steps);
    }

    public static boolean isValid(String expression) {
        return errorOf(expression) == null;
    }

    /** Returns the reason why the expression is not a valid pattern, or null when it is valid. */
    public static String errorOf(String expression) {
        if (expression == null || expression.isBlank()) {
            return "il pattern è vuoto";
        }
        for (String text : expression.strip().split("\\s+")) {
            if (!text.matches("0|[+-][1-9][0-9]?")) {
                return "passo non valido '" + text + "' (ammessi: 0, oppure un numero di semitoni con il segno, come +3 o -2)";
            }
            if (!text.equals("0") && Integer.parseInt(text.substring(1)) > MAX_STEP) {
                return "passo troppo ampio '" + text + "' (al massimo " + MAX_STEP + " semitoni)";
            }
        }
        return null;
    }

    /** Italian name of an interval of that many semitones, for example "sesta maggiore" for 9. */
    public static String nameOf(int semitones) {
        int size = Math.abs(semitones);
        return size < NAMES.length ? NAMES[size] : size + " semitoni";
    }
}
