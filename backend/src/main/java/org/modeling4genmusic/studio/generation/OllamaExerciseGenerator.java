package org.modeling4genmusic.studio.generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.modeling4genmusic.studio.generation.ValidationReport.Check;
import org.modeling4genmusic.studio.rhythm.RhythmPattern;
import org.springframework.stereotype.Component;

/**
 * Asks a music language model served by Ollama to write the exercise in ABC notation.
 * The request follows the form the model was trained on (a text description to turn into a score);
 * the header of the tune is written here from the spec, so the model only continues with the notes.
 */
@Component
public class OllamaExerciseGenerator implements ExerciseGenerator {

    static final String TASK = "Using ABC notation, recreate the given text as a musical score.";
    private static final String[] NOTE_NAMES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
    /** Enough for the requested bars with some margin; the tune is cut at the bar count anyway. */
    private static final int TOKENS_PER_BAR = 40;

    private final OllamaClient client;

    public OllamaExerciseGenerator(OllamaClient client) {
        this.client = client;
    }

    @Override
    public String id() {
        return "ollama";
    }

    @Override
    public String label() {
        return "Ollama";
    }

    @Override
    public Availability availability() {
        return client.availability();
    }

    @Override
    public boolean varies() {
        return true;
    }

    @Override
    public GenerationResult generate(EObject spec, GenerationContext ctx) {
        String meter = text(spec, "meter", "4/4");
        String key = text(spec, "key", "C");
        int bars = number(spec, "bars");
        int bpm = number(spec, "maxBpm") > 0 ? number(spec, "maxBpm") : number(spec, "requestedBpm");
        String tempo = bpm > 0 ? "1/4=" + bpm : null;

        String header = "X:1\nT:Esercizio\nL:1/8\n" + (tempo == null ? "" : "Q:" + tempo + "\n") + "M:" + meter + "\nK:" + key
                + "\n";
        long seed = ThreadLocalRandom.current().nextLong(1, Integer.MAX_VALUE);
        long start = System.nanoTime();
        String prompt = "Human: " + instruction(spec, client.feedback() ? ctx : new GenerationContext(ctx.qualityModel()), meter, key, tempo, bars) + " </s> Assistant: " + header;
        String answer = client.complete(prompt, seed, Math.max(8, bars) * TOKENS_PER_BAR);
        String abc = AbcExtractor.tune(header, answer, bars, barUnits(meter));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("generator", id());
        metadata.put("model", client.model());
        metadata.put("seed", seed);
        metadata.put("prompt", prompt);
        metadata.put("seconds", Math.round((System.nanoTime() - start) / 1e8) / 10.0);
        metadata.put("meter", meter);
        metadata.put("key", key);
        if (tempo != null) {
            metadata.put("tempo", tempo);
        }
        return new GenerationResult(abc, metadata);
    }

    /** The description of the exercise, in the wording of the model's training tasks. */
    static String instruction(EObject spec, GenerationContext ctx, String meter, String key, String tempo, int bars) {
        List<String> notes = new ArrayList<>();
        String instrument = literal(spec, "instrument");
        notes.add("A" + (bars > 0 ? " " + bars + " bar" : "") + " exercise for a beginner"
                + (instrument == null ? "" : " " + instrument.toLowerCase() + " player") + ".");
        // Several ranges (the learner's, a need's, the teacher's) are told once, as the one they all allow.
        List<EObject> ranges = children(spec, "constraints").stream()
                .filter(c -> c.eClass().getName().equals("RangeConstraint")).toList();
        for (EObject constraint : children(spec, "constraints")) {
            switch (constraint.eClass().getName()) {
                case "RangeConstraint" -> {
                    if (constraint == ranges.get(0)) {
                        int lowest = ranges.stream().mapToInt(c -> number(c, "lowestMidi")).max().getAsInt();
                        int highest = ranges.stream().mapToInt(c -> number(c, "highestMidi")).min().getAsInt();
                        notes.add("All notes stay between " + noteName(lowest) + " and " + noteName(highest) + ".");
                    }
                }
                case "ContainsPattern" -> rhythm(constraint).ifPresent(abc -> notes.add("It practises the rhythm "
                        + abc + ", used at least " + Math.max(1, number(constraint, "minOccurrences")) + " times."));
                case "ExcludesPattern" -> rhythm(constraint)
                        .ifPresent(abc -> notes.add("It never uses the rhythm " + abc + "."));
                case "SpanConstraint" -> notes.add("It is a single melodic line without chords, with no leap wider than "
                        + number(constraint, "maxStretchInPosition") + " semitones.");
                default -> {
                    // Rendering constraints do not concern the notes.
                }
            }
        }
        for (Check check : ctx.rejected()) {
            switch (check.name()) {
                case "range" -> notes.add("Keep every note inside that range: no lower and no higher notes.");
                case "containsPattern" -> notes.add("Use that rhythm more often.");
                case "excludesPattern" -> notes.add("Avoid the forbidden rhythm completely.");
                default -> {
                    // Other failures (number of bars, unreadable notation) are handled when the answer is tidied.
                }
            }
        }
        StringBuilder text = new StringBuilder(TASK).append("\nMeter ").append(meter).append("\nKey ").append(key)
                .append("\nNote Length 1/8");
        if (tempo != null) {
            text.append("\nTempo ").append(tempo);
        }
        return text.append("\nNotes ").append(String.join(" ", notes)).toString();
    }

    /** The rhythmic pattern of a constraint, written as ABC on two neighbouring notes. */
    private static java.util.Optional<String> rhythm(EObject constraint) {
        EObject pattern = children(constraint, "pattern").stream().findFirst().orElse(null);
        if (pattern == null || !"RHYTHM".equals(literal(pattern, "kind"))
                || !RhythmPattern.isValid(text(pattern, "expression", null))) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(abcOf(RhythmPattern.parse(text(pattern, "expression", null))));
    }

    /** With an eighth as unit length: the dotted pairs become broken rhythms, the rest explicit lengths. */
    static String abcOf(RhythmPattern pattern) {
        String normalized = pattern.normalized();
        if (normalized.equals("8. 16")) {
            return "c>B";
        }
        if (normalized.equals("16 8.")) {
            return "c<B";
        }
        String pitches = "cBAGFEDC";
        StringBuilder abc = new StringBuilder();
        int i = 0;
        for (RhythmPattern.Token token : pattern.tokens()) {
            // Length in quarters of an eighth note, the unit length of the tune.
            int sixteenths = 16 / token.duration() * (token.dotted() ? 3 : 2);
            abc.append(pitches.charAt(i++ % pitches.length()));
            abc.append(length(sixteenths, 4));
        }
        return abc.toString();
    }

    /** ABC length suffix of {@code numerator/denominator} unit lengths: nothing for 1, {@code /2} for a half. */
    private static String length(int numerator, int denominator) {
        while (denominator > 1 && numerator % 2 == 0) {
            numerator /= 2;
            denominator /= 2;
        }
        return (numerator == 1 ? "" : String.valueOf(numerator)) + (denominator == 1 ? "" : "/" + denominator);
    }

    static String noteName(int midi) {
        return NOTE_NAMES[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1);
    }

    private static int barUnits(String meter) {
        if (meter == null || !meter.matches("\\s*\\d+\\s*/\\s*\\d+\\s*")) {
            return 0;
        }
        String[] parts = meter.strip().split("\\s*/\\s*");
        int denominator = Integer.parseInt(parts[1]);
        return denominator == 0 ? 0 : AbcTune.WHOLE * Integer.parseInt(parts[0]) / denominator;
    }

    private static Object get(EObject object, String feature) {
        EStructuralFeature f = object.eClass().getEStructuralFeature(feature);
        return f == null ? null : object.eGet(f);
    }

    private static int number(EObject object, String feature) {
        return get(object, feature) instanceof Number n ? n.intValue() : 0;
    }

    private static String text(EObject object, String feature, String fallback) {
        return get(object, feature) instanceof String s && !s.isBlank() ? s.strip() : fallback;
    }

    private static String literal(EObject object, String feature) {
        return get(object, feature) instanceof EEnumLiteral l ? l.getName() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<EObject> children(EObject object, String feature) {
        Object value = get(object, feature);
        return value instanceof List<?> list ? (List<EObject>) list : value instanceof EObject o ? List.of(o) : List.of();
    }
}
