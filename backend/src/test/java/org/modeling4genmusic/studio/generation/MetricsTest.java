package org.modeling4genmusic.studio.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Each measure on tunes written by hand, where the expected value can be worked out on paper. */
class MetricsTest {

    private static final String HEADER = "X:1\nM:4/4\nL:1/8\nK:C\n";

    private static double measure(String metric, String notes) {
        return Metrics.byName(metric).orElseThrow().of(AbcTune.parse(HEADER + notes));
    }

    @Test
    void shortNoteShareIsThePartOfNotesShorterThanAQuarter() {
        assertThat(measure("shortNoteShare", "C2 D2 E2 F2 |]")).isZero();
        assertThat(measure("shortNoteShare", "C2 DE F2 GA |]")).isCloseTo(4 / 6.0, within(1e-9));
        assertThat(measure("shortNoteShare", "CDEF GABc |]")).isEqualTo(1);
        assertThat(measure("shortNoteShare", "z8 |]")).isZero();
    }

    @Test
    void rhythmVarietyCountsTheDifferentDurations() {
        assertThat(measure("rhythmVariety", "C2 D2 E2 F2 |]")).isZero();
        assertThat(measure("rhythmVariety", "C4 D2 EF |]")).isCloseTo(0.5, within(1e-9));
        // five durations or more: as varied as it gets
        assertThat(measure("rhythmVariety", "C8 | D4 E2 F G/2A/2 | B3 c3/2 z3 |]")).isEqualTo(1);
    }

    @Test
    void meanLeapIsTheAverageDistanceBetweenNeighbouringNotes() {
        assertThat(measure("meanLeap", "C2 C2 C2 C2 |]")).isZero();
        // C D E: two steps of two semitones, against an octave
        assertThat(measure("meanLeap", "C2 D2 E4 |]")).isCloseTo(2 / 12.0, within(1e-9));
        // rests do not break the line; an octave up and one down
        assertThat(measure("meanLeap", "C2 z2 c2 C2 |]")).isEqualTo(1);
        assertThat(measure("meanLeap", "C8 |]")).isZero();
    }

    @Test
    void rangeUseIsTheDistanceBetweenTheLowestAndTheHighestNote() {
        assertThat(measure("rangeUse", "C2 G2 C2 G2 |]")).isCloseTo(7 / 24.0, within(1e-9));
        assertThat(measure("rangeUse", "C2 c'2 C4 |]")).isEqualTo(1);
        assertThat(measure("rangeUse", "z8 |]")).isZero();
    }

    @Test
    void noteDensityIsTheNumberOfNotesInABeat() {
        assertThat(measure("noteDensity", "C2 D2 E2 F2 |]")).isCloseTo(0.25, within(1e-9));
        assertThat(measure("noteDensity", "CDEF GABc |]")).isCloseTo(0.5, within(1e-9));
        // rests count as time, not as notes
        assertThat(measure("noteDensity", "C2 z6 |]")).isCloseTo(1 / 16.0, within(1e-9));
    }

    @Test
    void theScriptsSeeWhichMetricsExist() {
        MetricTool tool = new MetricTool();
        assertThat(tool.isKnown("meanLeap")).isTrue();
        assertThat(tool.isKnown(" noteDensity ")).isTrue();
        assertThat(tool.isKnown("rhythmScore")).isFalse();
        assertThat(tool.isKnown(null)).isFalse();
        assertThat(tool.names()).hasSize(5).contains("rangeUse");
    }
}
