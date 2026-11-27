package org.modeling4genmusic.studio.generation;

import java.util.List;

/** The measures of {@link Metrics} as seen from the Epsilon scripts, as the {@code metrics} variable. */
public class MetricTool {

    public boolean isKnown(Object name) {
        return name != null && Metrics.byName(name.toString().strip()).isPresent();
    }

    public List<String> names() {
        return Metrics.all().stream().map(Metrics.Metric::name).toList();
    }
}
