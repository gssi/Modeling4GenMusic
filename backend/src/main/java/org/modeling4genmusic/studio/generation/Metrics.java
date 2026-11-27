package org.modeling4genmusic.studio.generation;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

import org.modeling4genmusic.studio.generation.AbcTune.Event;

/**
 * The measures that can be taken on an exercise, each between 0 and 1. A dimension of a quality model
 * names one of them and says which values make a level; so a dimension is added in a model, while a
 * new measure is added here.
 *
 * <p>The scales are plain and easy to read off a score; how well they follow the difficulty a
 * teacher perceives is still to be calibrated.
 */
public final class Metrics {

    /**
     * @param name        as written in the {@code metric} attribute of a quality dimension
     * @param description what it measures, for whoever edits a quality model
     */
    public record Metric(String name, String description, ToDoubleFunction<AbcTune> measure) {

        public double of(AbcTune tune) {
            return Math.max(0, Math.min(1, measure.applyAsDouble(tune)));
        }
    }

    private static final int QUARTER = AbcTune.WHOLE / 4;
    /** Leaps are measured against an octave, the range against two. */
    private static final double OCTAVE = 12;
    /** Four notes in a beat, sixteenth notes all along, is the densest writing considered. */
    private static final double NOTES_PER_BEAT_AT_FULL = 4;
    /** Whole, half, quarter, eighth and sixteenth: with all of them the variety is at its highest. */
    private static final double DURATIONS_AT_FULL = 5;

    private static final List<Metric> ALL = List.of(
            new Metric("shortNoteShare", "Quota di note più brevi di una semiminima", Metrics::shortNoteShare),
            new Metric("rhythmVariety", "Quante durate diverse compaiono (da una sola a cinque)", Metrics::rhythmVariety),
            new Metric("meanLeap", "Ampiezza media dei salti tra note vicine, rispetto a un'ottava", Metrics::meanLeap),
            new Metric("rangeUse", "Estensione usata, rispetto a due ottave", Metrics::rangeUse),
            new Metric("noteDensity", "Note per pulsazione, rispetto a quattro", Metrics::noteDensity));

    private Metrics() {
    }

    public static List<Metric> all() {
        return ALL;
    }

    public static Optional<Metric> byName(String name) {
        return ALL.stream().filter(m -> m.name().equals(name)).findFirst();
    }

    private static double shortNoteShare(AbcTune tune) {
        List<Event> notes = tune.notes();
        return notes.isEmpty() ? 0 : (double) notes.stream().filter(n -> n.units() < QUARTER).count() / notes.size();
    }

    private static double rhythmVariety(AbcTune tune) {
        long durations = tune.notes().stream().map(Event::units).distinct().count();
        return durations <= 1 ? 0 : (durations - 1) / (DURATIONS_AT_FULL - 1);
    }

    private static double meanLeap(AbcTune tune) {
        List<Event> notes = tune.notes();
        if (notes.size() < 2) {
            return 0;
        }
        double sum = 0;
        for (int i = 1; i < notes.size(); i++) {
            sum += Math.abs(notes.get(i).midi() - notes.get(i - 1).midi());
        }
        return sum / (notes.size() - 1) / OCTAVE;
    }

    private static double rangeUse(AbcTune tune) {
        List<Integer> pitches = tune.pitches();
        return pitches.isEmpty() ? 0
                : (pitches.stream().mapToInt(Integer::intValue).max().getAsInt()
                        - pitches.stream().mapToInt(Integer::intValue).min().getAsInt()) / (2 * OCTAVE);
    }

    private static double noteDensity(AbcTune tune) {
        double beats = tune.bars().stream().flatMap(List::stream).mapToInt(Event::units).sum() / (double) QUARTER;
        return beats == 0 ? 0 : tune.notes().size() / beats / NOTES_PER_BEAT_AT_FULL;
    }
}
