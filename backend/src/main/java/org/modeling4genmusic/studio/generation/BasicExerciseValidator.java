package org.modeling4genmusic.studio.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.modeling4genmusic.studio.generation.ValidationReport.Check;
import org.modeling4genmusic.studio.interval.IntervalPattern;
import org.modeling4genmusic.studio.rhythm.RhythmPattern;
import org.springframework.stereotype.Component;

/**
 * Checks of an exercise against its spec: number of bars, pitch range, occurrences of required and
 * excluded rhythm patterns, hand span, and the quality goals, each measured with the metric of its
 * dimension ({@link Metrics}). A quality goal is a remark, never a blocking failure.
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
                case "SpanConstraint" -> {
                    checks.add(handSpan(tune, constraint, hard));
                    checks.add(positionChanges(tune, constraint));
                }
                default -> {
                    // Rendering constraints concern how the score is shown, not the notes.
                }
            }
        }
        for (EObject goal : children(exerciseSpec, "qualityGoals")) {
            qualityGoal(tune, goal).ifPresent(checks::add);
        }
        return ValidationReport.of(checks);
    }

    /** Name of the checks on quality goals, which only say how close the exercise is to what was asked. */
    public static final String QUALITY_GOAL = "qualityGoal";

    /**
     * Measures the exercise with the metric of the goal's dimension and compares it with the band of
     * the level asked. Nothing is reported for a goal whose dimension cannot be read.
     */
    private static Optional<Check> qualityGoal(AbcTune tune, EObject goal) {
        if (!(get(goal, "dimension") instanceof EObject dimension) || dimension.eIsProxy()) {
            return Optional.empty();
        }
        String level = literal(goal, "level");
        String label = get(dimension, "label") instanceof String text && !text.isBlank() ? text
                : String.valueOf(get(dimension, "name"));
        String name = String.valueOf(get(dimension, "metric"));
        Optional<Metrics.Metric> metric = Metrics.byName(name);
        if (metric.isEmpty()) {
            return Optional.of(new Check(QUALITY_GOAL, false, false,
                    label + " " + level + ": la metrica «" + name + "» non è conosciuta, l'obiettivo non è stato misurato"));
        }
        EObject band = children(dimension, "bands").stream().filter(b -> level != null && level.equals(literal(b, "level")))
                .findFirst().orElse(null);
        double value = Math.round(metric.get().of(tune) * 100) / 100.0;
        if (band == null) {
            return Optional.of(new Check(QUALITY_GOAL, false, false, label + " " + level + ": " + name + " vale "
                    + value + ", ma il modello di qualità non dice che cosa significa " + level));
        }
        double min = ((Number) get(band, "min")).doubleValue();
        double max = ((Number) get(band, "max")).doubleValue();
        boolean inside = value >= min - 1e-9 && value <= max + 1e-9;
        return Optional.of(new Check(QUALITY_GOAL, inside, false, label + " " + level + ": " + name + " vale " + value
                + (inside ? ", entro " : ", fuori da ") + min + "–" + max));
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
        List<Integer> notes = tune.pitches();
        long outside = notes.stream().filter(n -> n < lowest || n > highest).count();
        if (outside == 0) {
            return new Check("range", true, hard,
                    "Tutte le note sono nell'estensione MIDI " + lowest + "–" + highest);
        }
        int min = notes.stream().mapToInt(Integer::intValue).min().orElse(lowest);
        int max = notes.stream().mapToInt(Integer::intValue).max().orElse(highest);
        return new Check("range", false, hard, outside + (outside == 1 ? " nota è" : " note sono")
                + " fuori dall'estensione MIDI " + lowest + "–" + highest + " (l'esercizio va da " + min + " a " + max
                + ")");
    }

    /** Notes played together must fit in the hand: a wider chord cannot be played at all. */
    private static Check handSpan(AbcTune tune, EObject constraint, boolean hard) {
        int limit = intOf(constraint, "maxSimultaneousSemitones");
        String hand = handName(constraint);
        String reach = "l'apertura della mano " + hand + " (" + limit + " semitoni, " + IntervalPattern.nameOf(limit) + ")";
        int widest = tune.notes().stream().mapToInt(AbcTune.Event::span).max().orElse(0);
        if (widest == 0) {
            return new Check("handSpan", true, hard, "Non ci sono note simultanee: " + reach + " è rispettata");
        }
        return widest <= limit
                ? new Check("handSpan", true, hard, "L'accordo più ampio copre " + widest + " semitoni ("
                        + IntervalPattern.nameOf(widest) + "), entro " + reach)
                : new Check("handSpan", false, hard, "Un accordo copre " + widest + " semitoni ("
                        + IntervalPattern.nameOf(widest) + "), oltre " + reach);
    }

    /** A melodic leap wider than the hand opens is playable by moving the hand: counted, never a failure. */
    private static Check positionChanges(AbcTune tune, EObject constraint) {
        int limit = intOf(constraint, "maxStretchInPosition");
        List<AbcTune.Event> notes = tune.notes();
        int changes = 0;
        for (int i = 1; i < notes.size(); i++) {
            if (Math.abs(notes.get(i).midi() - notes.get(i - 1).midi()) > limit) {
                changes++;
            }
        }
        String leaps = "salti melodici oltre " + limit + " semitoni (" + IntervalPattern.nameOf(limit) + ")";
        return new Check("positionChanges", true, false,
                changes == 0 ? "Nessun cambio di posizione della mano " + handName(constraint) + ": non ci sono " + leaps
                        : (changes == 1 ? "1 cambio" : changes + " cambi") + " di posizione della mano "
                                + handName(constraint) + ": " + leaps);
    }

    private static String handName(EObject constraint) {
        return "LEFT".equals(literal(constraint, "hand")) ? "sinistra" : "destra";
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
