package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChartsTest {

    static Ranking ranking(int... locations) {
        int total = java.util.Arrays.stream(locations).sum();
        List<Archetype> archetypes = java.util.stream.IntStream.range(0, locations.length)
                .mapToObj(i -> new Archetype(i + 1, "k" + i, locations[i], List.of("b" + i), "sig" + i, "b" + i,
                        List.of("org/repo")))
                .toList();
        return new Ranking(archetypes, total, 2, 3);
    }

    @Test
    void theCumulativeShareGrowsWithEachArchetypeInRankOrder() {
        assertThat(Charts.cumulative(ranking(50, 30, 20))).containsExactly(0.5, 0.8, 1.0);
    }

    @Test
    void theCoverageCurveRunsFromTheOriginToFullAndMarksK80AndK95() {
        String svg = Charts.coverage(ranking(50, 30, 20));

        assertThat(svg).startsWith("<svg").contains("role=\"img\"")
                .contains("points=\"44.0,212.0 238.7,112.0 433.3,52.0 628.0,12.0\"")
                .contains("<title>80% of locations: 2 archetypes</title>")
                .contains("<title>95% of locations: 3 archetypes</title>");
    }

    @Test
    void aLongCurveIsThinnedToTwoHundredPointsKeepingTheLast() {
        int[] locations = java.util.stream.IntStream.range(0, 1000).map(i -> 1).toArray();
        String points = Charts.coverage(ranking(locations)).replaceAll("(?s).*points=\"([^\"]*)\".*", "$1");

        assertThat(List.of(points.split(" "))).hasSizeLessThanOrEqualTo(202).last().isEqualTo("628.0,12.0");
    }

    @Test
    void aBasesBarIsAsLongAsItsLocationsColouredByStatusWithItsPinnedPartHatched() {
        String svg = Charts.bases(List.of(
                new BaseRow("ubi9/ubi-minimal", "9.4", "ubi-minimal", 700, 200, "supported", null),
                new BaseRow("centos", "centos7", "centos", 350, 0, "eol", null)));

        assertThat(svg).startsWith("<svg").contains(
                "<rect class=\"bar supported\" width=\"330.0\"",
                "<rect class=\"pinned\" width=\"94.3\"",
                "<rect class=\"bar eol\" width=\"165.0\"",
                "<title>ubi9/ubi-minimal:9.4: 700 locations, 200 pinned by digest, supported</title>",
                ">centos:centos7</text>");
    }

    @Test
    void aStackedBarSplitsItsTotalInTheGivenOrderWithGapsAndALegendOfCountsAndShares() {
        String html = Charts.stacked("Users", List.of(
                new Charts.Segment("a", 50), new Charts.Segment("b", 30), new Charts.Segment("c", 20)));

        assertThat(html).contains(
                "<rect class=\"seg s1\" x=\"20.0\" width=\"298.0\"",
                "<rect class=\"seg s2\" x=\"320.0\" width=\"178.8\"",
                "<rect class=\"seg s3\" x=\"500.8\" width=\"119.2\"",
                "<span class=\"key s1\"></span>a 50 (50.0%)",
                "<span class=\"key s3\"></span>c 20 (20.0%)");
    }

    @Test
    void moreThanEightPartsFoldTheRestIntoOther() {
        List<Charts.Segment> parts = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new Charts.Segment("p" + i, 10)).toList();

        assertThat(Charts.stacked("Managers", parts))
                .contains("<span class=\"key s8\"></span>other 30 (30.0%)").doesNotContain("p9");
    }
}
