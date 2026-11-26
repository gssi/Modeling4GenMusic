package org.modeling4genmusic.studio.generation;

/**
 * Computes a quality metric (for example {@code rhythmScore} or {@code melodicScore}) of an exercise,
 * as a value between 0 and 1 to be compared with the bands of the quality model.
 * <p>
 * TODO: no implementation yet. The definition and calibration of the metrics are still open, so the
 * quality goals of a spec are not verified.
 */
public interface MetricCalculator {

    /** Name of the metric, as written in the metric bindings of the quality model. */
    String metric();

    double compute(String abc);
}
