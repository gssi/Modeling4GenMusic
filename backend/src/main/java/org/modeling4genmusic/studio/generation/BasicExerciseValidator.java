package org.modeling4genmusic.studio.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.modeling4genmusic.studio.generation.ValidationReport.Check;
import org.modeling4genmusic.studio.rhythm.RhythmPattern;
import org.springframework.stereotype.Component;

/**
 * Checks that need no metric: number of bars, pitch range, occurrences of required and excluded
 * rhythm patterns. Quality goals are not verified: they wait for a {@link MetricCalculator}.
 * <p>
 * A rhythm pattern occurs where its durations appear on consecutive notes of the same bar.
 */
@Component
public class BasicExerciseValidator implements ExerciseValidator {

    @Override
    public ValidationReport validate(String abc, EObject exerciseSpec, EObject qualityModel) {
        AbcTune tune;
        try {
            tune = AbcTune.parse(abc);
        } catch (IllegalArgumentException e) {
            return ValidationReport.of(
                    List.of(new Check("abc", false, true, "L'ABC non è leggibile: " + e.getMessage())));
        }
        List<Check> checks = new ArrayList<>();
        int bars = intOf(exerciseSpec, "bars");
        if (bars > 0) {
            int actual = tune.bars().size();
            checks.add(new Check("bars", actual == bars, true,
                    actual == bars ? "Le battute sono " + bars + ", come richiesto"
                            : "Le battute sono " + actual + " invece di " + bars));
        }
        barLength(tune, get(exerciseSpec, "meter")).ifPresent(checks::add);
        for (EObject constraint : children(exerciseSpec, "constraints")) {
            boolean hard = "HARD".equals(literal(constraint, "strength"));
            switch (constraint.eClass().getName()) {
                case "RangeConstraint" -> checks.add(range(tune, constraint, hard));
                case "ContainsPattern" -> checks.add(pattern(tune, constraint, hard, true));
                case "ExcludesPattern" -> checks.add(pattern(tune, constraint, hard, false));
                default -> {
                    // Rendering constraints concern how the score is shown, not the notes.
                }
            }
        }
        return ValidationReport.of(checks);
    }

    /**
     * Reports the bars whose length differs from the meter. It is a remark, not a blocking check:
     * the spec only fixes the number of bars.
     */
    private static Optional<Check> barLength(AbcTune tune, Object meter) {
        if (!(meter instanceof String text) || !text.matches("\\s*\\d+\\s*/\\s*\\d+\\s*")) {
            return Optional.empty();
        }
        String[] parts = text.strip().split("\\s*/\\s*");
        int expected = AbcTune.WHOLE * Integer.parseInt(parts[0]) / Integer.parseInt(parts[1]);
        List<Integer> wrong = new ArrayList<>();
        for (int i = 0; i < tune.bars().size(); i++) {
            int length = tune.bars().get(i).stream().mapToInt(AbcTune.Event::units).sum();
            if (length != expected) {
                wrong.add(i + 1);
            }
        }
        if (wrong.isEmpty()) {
            return Optional.of(new Check("barLength", true, false, "Ogni battuta dura " + text.strip()));
        }
        String list = wrong.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        return Optional.of(new Check("barLength", false, false, (wrong.size() == 1 ? "La battuta " + list + " non dura "
                : "Le battute " + list + " non durano ") + text.strip()));
    }

    private static Check range(AbcTune tune, EObject constraint, boolean hard) {
        int lowest = intOf(constraint, "lowestMidi");
        int highest = intOf(constraint, "highestMidi");
        List<AbcTune.Event> notes = tune.notes();
        long outside = notes.stream().filter(n -> n.midi() < lowest || n.midi() > highest).count();
        if (outside == 0) {
            return new Check("range", true, hard,
                    "Tutte le note sono nell'estensione MIDI " + lowest + "–" + highest);
        }
        int min = notes.stream().mapToInt(AbcTune.Event::midi).min().orElse(lowest);
        int max = notes.stream().mapToInt(AbcTune.Event::midi).max().orElse(highest);
        return new Check("range", false, hard, outside + (outside == 1 ? " nota è" : " note sono")
                + " fuori dall'estensione MIDI " + lowest + "–" + highest + " (l'esercizio va da " + min + " a " + max
                + ")");
    }

    private static Check pattern(AbcTune tune, EObject constraint, boolean hard, boolean required) {
        String name = required ? "containsPattern" : "excludesPattern";
        EObject pattern = children(constraint, "pattern").stream().findFirst().orElse(null);
        String expression = pattern == null ? null : (String) get(pattern, "expression");
        String kind = pattern == null ? null : literal(pattern, "kind");
        if (pattern == null || !"RHYTHM".equals(kind)) {
            return new Check(name, true, false, "Pattern di tipo " + kind + " non verificato: sono controllati solo quelli ritmici");
        }
        if (!RhythmPattern.isValid(expression)) {
            return new Check(name, false, hard,
                    "Pattern ritmico non valido '" + expression + "': " + RhythmPattern.errorOf(expression));
        }
        int occurrences = occurrences(tune, RhythmPattern.parse(expression));
        if (required) {
            int minimum = Math.max(1, intOf(constraint, "minOccurrences"));
            boolean passed = occurrences >= minimum;
            return new Check(name, passed, hard, "Il pattern '" + expression + "' compare " + times(occurrences)
                    + (passed ? "" : ", meno di") + (passed ? " (richieste almeno " + minimum + ")" : " " + minimum + " richieste"));
        }
        return new Check(name, occurrences == 0, hard, occurrences == 0
                ? "Il pattern escluso '" + expression + "' non compare"
                : "Il pattern escluso '" + expression + "' compare " + times(occurrences));
    }

    private static String times(int n) {
        return n == 1 ? "1 volta" : n + " volte";
    }

    static int occurrences(AbcTune tune, RhythmPattern pattern) {
        List<Integer> wanted = pattern.tokens().stream().map(RhythmPattern.Token::units).toList();
        int count = 0;
        for (List<AbcTune.Event> bar : tune.bars()) {
            for (int start = 0; start + wanted.size() <= bar.size(); start++) {
                boolean match = true;
                for (int i = 0; i < wanted.size() && match; i++) {
                    AbcTune.Event event = bar.get(start + i);
                    match = !event.isRest() && event.units() == wanted.get(i);
                }
                if (match) {
                    count++;
                }
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- reflective access to the spec

    private static Object get(EObject object, String feature) {
        EStructuralFeature f = object.eClass().getEStructuralFeature(feature);
        return f == null ? null : object.eGet(f);
    }

    private static int intOf(EObject object, String feature) {
        return get(object, feature) instanceof Number number ? number.intValue() : 0;
    }

    private static String literal(EObject object, String feature) {
        return get(object, feature) instanceof EEnumLiteral literal ? literal.getName() : null;
    }

    private static List<EObject> children(EObject object, String feature) {
        Object value = get(object, feature);
        if (value instanceof List<?> list) {
            return list.stream().filter(EObject.class::isInstance).map(EObject.class::cast).toList();
        }
        return value instanceof EObject child ? List.of(child) : List.of();
    }
}
