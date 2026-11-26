package org.modeling4genmusic.studio.rhythm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rhythmic pattern written as duration tokens separated by spaces.
 * <pre>
 * pattern  := token (SPACE+ token)*
 * token    := duration '.'?
 * duration := '1' | '2' | '4' | '8' | '16'
 * </pre>
 * {@code 4} is a quarter note, {@code 8} an eighth, {@code 16} a sixteenth; a trailing dot adds half of the
 * value. Example: {@code "8. 16"}. The frontend parser must accept exactly the same language.
 */
public final class RhythmPattern {

    private static final Set<String> DURATIONS = Set.of("1", "2", "4", "8", "16");
    /** Units in a whole note: 192, so that dotted sixteenths and triplets are still integers. */
    public static final int WHOLE = 192;

    /** One duration token; {@code units} is its length in units of 1/192 of a whole note. */
    public record Token(int duration, boolean dotted) {

        public int units() {
            int base = WHOLE / duration;
            return dotted ? base + base / 2 : base;
        }

        @Override
        public String toString() {
            return duration + (dotted ? "." : "");
        }
    }

    private final List<Token> tokens;

    private RhythmPattern(List<Token> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    public List<Token> tokens() {
        return tokens;
    }

    /** Total length in units of 1/192 of a whole note. */
    public int units() {
        return tokens.stream().mapToInt(Token::units).sum();
    }

    /** Canonical text: tokens separated by one space. */
    public String normalized() {
        return String.join(" ", tokens.stream().map(Token::toString).toList());
    }

    /** Parses an expression or throws {@link IllegalArgumentException} with the reason. */
    public static RhythmPattern parse(String expression) {
        String error = errorOf(expression);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        List<Token> tokens = new ArrayList<>();
        for (String text : expression.strip().split("\\s+")) {
            boolean dotted = text.endsWith(".");
            tokens.add(new Token(Integer.parseInt(dotted ? text.substring(0, text.length() - 1) : text), dotted));
        }
        return new RhythmPattern(tokens);
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
            String digits = text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
            if (!DURATIONS.contains(digits)) {
                return "token non valido '" + text + "' (ammessi: 1, 2, 4, 8, 16, con punto opzionale)";
            }
        }
        return null;
    }

    /** Canonical form of a valid expression; an invalid one is returned trimmed, unchanged otherwise. */
    public static String normalize(String expression) {
        if (expression == null) {
            return "";
        }
        return isValid(expression) ? parse(expression).normalized() : expression.strip();
    }
}
