package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CoverageChartTest {

    static Ranking ranking(int... locations) {
        int total = Arrays.stream(locations).sum();
        List<Archetype> archetypes = IntStream.range(0, locations.length)
                .mapToObj(i -> new Archetype(i + 1, "k" + i, locations[i], List.of("b" + i), "sig" + i, "b" + i,
                        List.of("org/repo")))
                .toList();
        return new Ranking(archetypes, total, 2, 3);
    }

    @Test
    void theCumulativeShareGrowsWithEachArchetypeInRankOrder() {
        assertThat(CoverageChart.cumulative(ranking(50, 30, 20))).containsExactly(0.5, 0.8, 1.0);
    }

    @Test
    void theCumulativeShareIsZeroWhenNoLocationsAreCovered() {
        assertThat(CoverageChart.cumulative(new Ranking(List.of(new Archetype(1, "k", 0, List.of(), "s", "b",
                List.of())), 0, 0, 0))).containsExactly(0.0);
    }

    @Test
    void theCoverageCurveRunsFromTheOriginToFullAndMarksK80AndK95() {
        String svg = CoverageChart.render(ranking(50, 30, 20));

        assertThat(svg).startsWith("<svg").contains("role=\"img\"")
                .contains("points=\"44.0,212.0 238.7,112.0 433.3,52.0 628.0,12.0\"")
                .contains("<title>80% of locations: 2 archetypes</title>")
                .contains("<title>95% of locations: 3 archetypes</title>");
    }

    @Test
    void aLongCurveIsThinnedToTwoHundredPointsKeepingTheLast() {
        List<Double> shares = IntStream.rangeClosed(1, 1000).mapToObj(i -> i / 1000.0).toList();

        assertThat(List.of(CoverageChart.points(shares).split(" ")))
                .hasSizeLessThanOrEqualTo(202).last().isEqualTo("628.0,12.0");
    }

    @Test
    void aGridLineIsDrawnAtItsShareWithItsLabelOnTheAxis() {
        assertThat(CoverageChart.grid(0.5, "50%")).isEqualTo("<line class=\"grid\" x1=\"44\" y1=\"112.0\" x2=\"628\" "
                + "y2=\"112.0\"/><text class=\"axis\" x=\"38\" y=\"116.0\" text-anchor=\"end\">50%</text>");
    }

    @Test
    void aMarkerOutsideTheCurveIsLeftOut() {
        List<Double> shares = List.of(0.5, 1.0);

        assertThat(CoverageChart.marker(0, shares, "80%")).isEmpty();
        assertThat(CoverageChart.marker(3, shares, "80%")).isEmpty();
        assertThat(CoverageChart.marker(2, shares, "95%")).contains("<circle cx=\"628.0\" cy=\"12.0\" r=\"5\">")
                .contains(">95% at 2</text>");
    }

    @Test
    void xRunsAcrossThePlotByRankAndYUpItByShare() {
        assertThat(CoverageChart.x(0, 0)).isEqualTo(44.0);
        assertThat(CoverageChart.x(1, 2)).isEqualTo(336.0);
        assertThat(CoverageChart.y(0)).isEqualTo(212.0);
        assertThat(CoverageChart.y(1)).isEqualTo(12.0);
    }
}
