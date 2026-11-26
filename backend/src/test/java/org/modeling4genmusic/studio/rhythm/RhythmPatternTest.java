package org.modeling4genmusic.studio.rhythm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The valid and invalid cases below are the reference for the frontend parser too. */
class RhythmPatternTest {

    @ParameterizedTest
    @ValueSource(strings = { "4", "8 8", "8. 16", "16 8.", "1", "2. 4", "16 16 16 16", "  8.   16 ", "4\t4" })
    void acceptsValidPatterns(String expression) {
        assertThat(RhythmPattern.isValid(expression)).isTrue();
        assertThat(RhythmPattern.errorOf(expression)).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   ", "3", "32", "8..", ".8", "8 .", "4,4", "8.16", "a", "-4", "04", "8 x 16" })
    void rejectsInvalidPatterns(String expression) {
        assertThat(RhythmPattern.isValid(expression)).isFalse();
        assertThat(RhythmPattern.errorOf(expression)).isNotBlank();
        assertThatThrownBy(() -> RhythmPattern.parse(expression)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorNamesTheOffendingToken() {
        assertThat(RhythmPattern.errorOf("8 x 16")).contains("'x'");
        assertThat(RhythmPattern.errorOf("8. 32")).contains("'32'");
        assertThat(RhythmPattern.errorOf(" ")).contains("vuoto");
    }

    @Test
    void parsesTokensAndLength() {
        RhythmPattern pattern = RhythmPattern.parse("  8.   16 ");
        assertThat(pattern.tokens()).containsExactly(new RhythmPattern.Token(8, true),
                new RhythmPattern.Token(16, false));
        assertThat(pattern.normalized()).isEqualTo("8. 16");
        // dotted eighth + sixteenth = one quarter note
        assertThat(pattern.units()).isEqualTo(RhythmPattern.parse("4").units());
        assertThat(RhythmPattern.parse("1").units()).isEqualTo(4 * RhythmPattern.parse("4").units());
    }

    @Test
    void normalizeIsLenientOnInvalidInput() {
        assertThat(RhythmPattern.normalize(" 8.  16 ")).isEqualTo("8. 16");
        assertThat(RhythmPattern.normalize(" foo ")).isEqualTo("foo");
        assertThat(RhythmPattern.normalize(null)).isEmpty();
    }
}
