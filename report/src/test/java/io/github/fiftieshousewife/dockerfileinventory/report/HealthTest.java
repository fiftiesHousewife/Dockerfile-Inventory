package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
                tuple("Parse failures", State.GOOD),
                tuple("Final user root", State.ACT),
                tuple("Bases pinned by digest", State.WATCH),
                tuple("Bases at end of life", State.GOOD),
                tuple("Scanned at a released commit", State.GOOD),
                tuple("RUN too complex to read", State.ACT));
    }

    @Test
    void theRootShareIsOfAllUsersAndZeroWithNone() {
        assertThat(Health.rootShare(summary(Map.of("ROOT", 1, "NAMED", 3)))).isEqualTo(0.25);
        assertThat(Health.rootShare(summary(Map.of()))).isZero();
    }

    @Test
    void lowerIsBetterIsGoodAtTheGoodThresholdAndActOnlyAboveTheActThreshold() {
        assertThat(Health.lowerIsBetter("m", 0.1, 0.1, 0.3).state()).isEqualTo(State.GOOD);
        assertThat(Health.lowerIsBetter("m", 0.3, 0.1, 0.3).state()).isEqualTo(State.WATCH);
        assertThat(Health.lowerIsBetter("m", 0.31, 0.1, 0.3).state()).isEqualTo(State.ACT);
    }

    @Test
    void higherIsBetterIsGoodAtTheGoodThresholdAndActOnlyBelowTheActThreshold() {
        assertThat(Health.higherIsBetter("m", 0.8, 0.8, 0.2).state()).isEqualTo(State.GOOD);
        assertThat(Health.higherIsBetter("m", 0.2, 0.8, 0.2).state()).isEqualTo(State.WATCH);
        assertThat(Health.higherIsBetter("m", 0.19, 0.8, 0.2).state()).isEqualTo(State.ACT);
    }

    private static Summary summary(Map<String, Integer> users) {
        return new Summary(0, 0, List.of(), 0, 0, 0, 0, 0, 0, 0, Map.of(), users, false, 0, 0, 0);
    }
}
