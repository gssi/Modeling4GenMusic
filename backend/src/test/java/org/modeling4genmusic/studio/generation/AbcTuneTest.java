package org.modeling4genmusic.studio.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.modeling4genmusic.studio.generation.AbcTune.Event;
import org.modeling4genmusic.studio.rhythm.RhythmPattern;

class AbcTuneTest {

    private static AbcTune tune(String key, String unit, String body) {
        return AbcTune.parse("X:1\nM:4/4\nL:" + unit + "\nK:" + key + "\n" + body + "\n");
    }

    @Test
    void readsPitchesWithOctaveMarksAndKeySignature() {
        AbcTune tune = tune("G", "1/4", "C D F f | c' C, B, A |");
        assertThat(tune.bars()).hasSize(2);
        // F is sharp in G major; middle C is 60.
        assertThat(tune.notes()).extracting(Event::midi).containsExactly(60, 62, 66, 78, 84, 48, 59, 69);
    }

    @Test
    void accidentalsLastUntilTheBarLineAndNaturalsCancelTheKey() {
        AbcTune tune = tune("G", "1/4", "=F F ^C C | F C _B, z |");
        assertThat(tune.bars().get(0)).extracting(Event::midi).containsExactly(65, 65, 61, 61);
        assertThat(tune.bars().get(1)).extracting(Event::midi).containsExactly(66, 60, 58, -1);
        assertThat(tune.notes()).hasSize(7);
    }

    @Test
    void readsFlatAndMinorKeys() {
        assertThat(tune("Bb", "1/4", "B E A").notes()).extracting(Event::midi).containsExactly(70, 63, 69);
        assertThat(tune("Dm", "1/4", "B").notes()).extracting(Event::midi).containsExactly(70);
        assertThat(tune("F#m", "1/4", "F C G D").notes()).extracting(Event::midi).containsExactly(66, 61, 68, 62);
    }

    @Test
    void readsLengthsInSixtyFourthsIncludingBrokenRhythm() {
        AbcTune tune = tune("C", "1/8", "C2 D E/ F/2 G4 | c>B A<G z2 C3/2 |]");
        assertThat(tune.bars().get(0)).extracting(Event::units).containsExactly(48, 24, 12, 12, 96);
        assertThat(tune.bars().get(1)).extracting(Event::units).containsExactly(36, 12, 12, 36, 48, 36);
    }

    @Test
    void ignoresAnnotationsTiesAndSlursAndUsesTheDefaultUnit() {
        AbcTune tune = AbcTune.parse("K:C\n\"Am\"(C2 D2-) D2 !f! E2 | % comment\n G8 ||");
        assertThat(tune.bars()).hasSize(2);
        assertThat(tune.bars().get(0)).extracting(Event::units).containsExactly(48, 48, 48, 48);
    }

    @Test
    void rejectsWhatItCannotRead() {
        assertThatThrownBy(() -> AbcTune.parse("")).hasMessageContaining("vuoto");
        assertThatThrownBy(() -> AbcTune.parse("M:4/4\nC D E F")).hasMessageContaining("K:");
        assertThatThrownBy(() -> tune("H", "1/8", "C")).hasMessageContaining("tonalità");
        assertThatThrownBy(() -> tune("C", "1/8", "C2 [K:G] D2")).hasMessageContaining("in mezzo al brano");
        assertThatThrownBy(() -> tune("C", "1/8", "[CE")).hasMessageContaining("accordo");
        assertThatThrownBy(() -> tune("C", "1/8", "(5CDEFG")).hasMessageContaining("irregolari");
        assertThatThrownBy(() -> tune("C", "1/8", "C ? D")).hasMessageContaining("'?'");
    }

    @Test
    void countsRhythmPatternsWithinABar() {
        AbcTune tune = tune("C", "1/8", "c>B A2 c>B z2 | c3/2 z/ B/ c>B c | c<B c>");
        assertThat(BasicExerciseValidator.occurrences(tune, RhythmPattern.parse("8. 16"))).isEqualTo(3);
        // Once written with "<", once as a sixteenth followed by the long note of a ">" pair.
        assertThat(BasicExerciseValidator.occurrences(tune, RhythmPattern.parse("16 8."))).isEqualTo(2);
        assertThat(BasicExerciseValidator.occurrences(tune, RhythmPattern.parse("4"))).isEqualTo(1);
        // Two eighths in a row never happen: they are always split by a bar line or a different length.
        assertThat(BasicExerciseValidator.occurrences(tune, RhythmPattern.parse("8 8"))).isZero();
    }

    @Test
    void theMockExerciseHasEightBars() {
        AbcTune tune = AbcTune.parse(MockExerciseGenerator.ABC);
        assertThat(tune.bars()).hasSize(8);
        assertThat(tune.headers()).containsEntry("M", "4/4").containsEntry("K", "G").containsEntry("Q", "1/4=72");
        assertThat(tune.notes()).extracting(Event::midi).allMatch(m -> m >= 60 && m <= 79);
        assertThat(tune.bars().stream().map(b -> b.stream().mapToInt(Event::units).sum()).toList())
                .isEqualTo(List.of(192, 144, 192, 144, 192, 144, 144, 192));
    }

    @Test
    void readsTupletsAndSkipsGraceNotes() {
        AbcTune tune = AbcTune.parse("X:1\nL:1/8\nM:4/4\nK:G\n(3(Bcd) e2 {f}g2 (3A2B2c2 | (2AB (4GABc z2 |]");
        // a triplet of eighths lasts a quarter; a triplet of quarters lasts a half
        assertThat(tune.bars().get(0)).extracting(Event::units).containsExactly(16, 16, 16, 48, 48, 32, 32, 32);
        assertThat(tune.bars().get(0).stream().mapToInt(Event::units).sum()).isEqualTo(AbcTune.WHOLE + 48);
        assertThat(tune.bars().get(1)).extracting(Event::units).containsExactly(36, 36, 18, 18, 18, 18, 48);
    }

    @Test
    void readsChordsAsNotesPlayedTogether() {
        AbcTune tune = tune("G", "1/8", "[Gg]2 [CEG]4 [c2e2] F | [B,D]>[DF] z2 |]");
        List<Event> first = tune.bars().get(0);
        assertThat(first).extracting(Event::units).containsExactly(48, 96, 48, 24);
        assertThat(first.get(0).pitches()).containsExactly(67, 79);
        assertThat(first.get(0).midi()).isEqualTo(79);
        assertThat(first.get(0).span()).isEqualTo(12);
        assertThat(first.get(1).pitches()).containsExactly(60, 64, 67);
        assertThat(first.get(1).span()).isEqualTo(7);
        // the key signature applies inside chords too: F is sharp in G major
        assertThat(first.get(3).pitches()).containsExactly(66);
        assertThat(first.get(3).span()).isZero();
        assertThat(tune.bars().get(1)).extracting(Event::units).containsExactly(36, 12, 48);
        assertThat(tune.pitches()).contains(59, 62, 66, 79);
    }
}
