package org.modeling4genmusic.studio.interval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The frontend parser (frontend/src/domain/interval.ts) is tested on the same cases. */
class IntervalPatternTest {

    @ParameterizedTest
    @ValueSource(strings = { "+12", "+3 -2 +12", "0", "+1 0 -1", "  +7   -7  ", "-24", "+10" })
    void acceptsValidPatterns(String expression) {
        assertThat(IntervalPattern.isValid(expression)).isTrue();
        assertThat(IntervalPattern.errorOf(expression)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "12", "3 -2", "+0", "-0", "+03", "+2.5", "+x", "++3", "+25", "-100", "+3,-2" })
    void rejectsInvalidPatterns(String expression) {
        assertThat(IntervalPattern.isValid(expression)).isFalse();
        assertThat(IntervalPattern.errorOf(expression)).isNotBlank();
        assertThatThrownBy(() -> IntervalPattern.parse(expression)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namesTheOffendingStep() {
        assertThat(IntervalPattern.errorOf("+3 12")).contains("'12'");
        assertThat(IntervalPattern.errorOf("+25")).contains("troppo ampio");
        assertThat(IntervalPattern.errorOf(null)).contains("vuoto");
    }

    @Test
    void readsStepsAndTheWidestLeap() {
        IntervalPattern pattern = IntervalPattern.parse(" +3  -2 +12 ");
        assertThat(pattern.steps()).containsExactly(3, -2, 12);
        assertThat(pattern.maxLeap()).isEqualTo(12);
        assertThat(pattern.normalized()).isEqualTo("+3 -2 +12");
        assertThat(IntervalPattern.parse("-9 +4").maxLeap()).isEqualTo(9);
        assertThat(IntervalPattern.parse("0").maxLeap()).isZero();
    }

    @Test
    void namesIntervals() {
        assertThat(IntervalPattern.nameOf(9)).isEqualTo("sesta maggiore");
        assertThat(IntervalPattern.nameOf(-12)).isEqualTo("ottava");
        assertThat(IntervalPattern.nameOf(16)).isEqualTo("decima maggiore");
        assertThat(IntervalPattern.nameOf(20)).isEqualTo("20 semitoni");
        IntervalTool tool = new IntervalTool();
        assertThat(tool.maxLeap("+12")).isEqualTo(12);
        assertThat(tool.maxLeap(null)).isZero();
        assertThat(tool.maxLeap("x")).isZero();
        assertThat(tool.nameOf(9)).isEqualTo("sesta maggiore");
    }
}
