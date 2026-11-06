package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.Health.Metric;
import io.github.fiftieshousewife.dockerfileinventory.report.Health.State;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthTest {

    @Test
    void eachMetricIsGoodAtOrBetterThanItsGoodThresholdActWhenWorseThanItsActThresholdAndWatchBetween() {
        Summary summary = new Summary(100, 0, List.of(), 0, 0, 0, 0.01, 0.16, 0.5, 0.0,
                Map.of(), Map.of("ROOT", 31, "NUMERIC", 69), false, 0.95, 0, 0);

        assertThat(Health.of(summary)).extracting(Metric::name, Metric::state).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Parse failures", State.GOOD),
                org.assertj.core.groups.Tuple.tuple("Final user root", State.ACT),
                org.assertj.core.groups.Tuple.tuple("Bases pinned by digest", State.WATCH),
                org.assertj.core.groups.Tuple.tuple("Bases at end of life", State.GOOD),
                org.assertj.core.groups.Tuple.tuple("Scanned at a released commit", State.GOOD),
                org.assertj.core.groups.Tuple.tuple("RUN too complex to read", State.ACT));
    }
}
