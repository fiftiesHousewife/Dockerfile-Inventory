package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.fiftieshousewife.dockerfileinventory.report.Figures.Count;
import io.github.fiftieshousewife.dockerfileinventory.report.Health.Metric;
import io.github.fiftieshousewife.dockerfileinventory.report.Health.State;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthTest {

    @Test
    void eachMetricIsGoodAtOrBetterThanItsGoodThresholdActWhenWorseThanItsActThresholdAndWatchBetween() {
        Figures figures = figures("estate", Map.of(Health.PARSE_FAILURES, new Count(1, 100, true),
                Health.ROOT, new Count(31, 100, true), Health.PINNED, new Count(50, 100, true),
                Health.EOL, new Count(0, 100, true), Health.RELEASED, new Count(95, 100, true),
                Health.OPAQUE, new Count(16, 100, true)));

        assertThat(Health.of(figures)).extracting(Metric::name, Metric::state).containsExactly(
                tuple(Health.PARSE_FAILURES, State.GOOD),
                tuple(Health.ROOT, State.ACT),
                tuple(Health.PINNED, State.WATCH),
                tuple(Health.EOL, State.GOOD),
                tuple(Health.RELEASED, State.GOOD),
                tuple(Health.OPAQUE, State.ACT));
    }

    @Test
    void withoutAnEndOfLifeListOrAReleaseIndexTheStripSaysNoData() {
        Figures figures = figures("estate", Map.of(Health.EOL, new Count(0, 100, false),
                Health.RELEASED, new Count(0, 100, false)));

        assertThat(Health.of(figures)).extracting(Metric::state).containsExactly(State.NO_DATA, State.NO_DATA);
        assertThat(State.NO_DATA.word()).isEqualTo("no data");
    }

    @Test
    void theMainContributorIsTheOrganisationWithTheMostLocationsOnTheWrongSide() {
        List<Figures> organisations = List.of(
                figures("acme", Map.of(Health.ROOT, new Count(10, 20, true), Health.PINNED, new Count(5, 10, true))),
                figures("beta", Map.of(Health.ROOT, new Count(30, 90, true), Health.PINNED, new Count(1, 4, true))));

        assertThat(Health.contributor(Health.ROOT, organisations))
                .hasValueSatisfying(c -> assertThat(c).isEqualTo(new Health.Contributor("beta", 30, 40)));
        assertThat(Health.contributor(Health.PINNED, organisations))
                .hasValueSatisfying(c -> assertThat(c).isEqualTo(new Health.Contributor("acme", 5, 8)));
    }

    @Test
    void thereIsNoContributorWhenNothingIsOnTheWrongSideOrTheDataIsMissing() {
        List<Figures> organisations = List.of(figures("acme", Map.of(Health.ROOT, new Count(0, 20, true),
                Health.EOL, new Count(5, 20, false))));

        assertThat(Health.contributor(Health.ROOT, organisations)).isEmpty();
        assertThat(Health.contributor(Health.EOL, organisations)).isEmpty();
    }

    @Test
    void lowerIsBetterIsGoodAtTheGoodThresholdAndActOnlyAboveTheActThreshold() {
        assertThat(Health.lowerIsBetter(0.1, 0.1, 0.3)).isEqualTo(State.GOOD);
        assertThat(Health.lowerIsBetter(0.3, 0.1, 0.3)).isEqualTo(State.WATCH);
        assertThat(Health.lowerIsBetter(0.31, 0.1, 0.3)).isEqualTo(State.ACT);
    }

    @Test
    void higherIsBetterIsGoodAtTheGoodThresholdAndActOnlyBelowTheActThreshold() {
        assertThat(Health.higherIsBetter(0.8, 0.8, 0.2)).isEqualTo(State.GOOD);
        assertThat(Health.higherIsBetter(0.2, 0.8, 0.2)).isEqualTo(State.WATCH);
        assertThat(Health.higherIsBetter(0.19, 0.8, 0.2)).isEqualTo(State.ACT);
    }

    static Figures figures(String name, Map<String, Count> counts) {
        Map<String, Count> ordered = new LinkedHashMap<>();
        Health.NAMES.stream().filter(counts::containsKey).forEach(n -> ordered.put(n, counts.get(n)));
        return new Figures(name, 0, 0, 0, new Figures.Apart(0, 0), new Figures.Apart(0, 0), Map.of(), Map.of(),
                Map.of(), "", 0, ordered);
    }
}
