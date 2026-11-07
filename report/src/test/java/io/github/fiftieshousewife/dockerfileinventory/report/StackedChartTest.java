package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.StackedChart.Segment;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StackedChartTest {

    @Test
    void aStackedBarSplitsItsTotalInTheGivenOrderWithGapsAndALegendOfCountsAndShares() {
        String html = StackedChart.render("Users", List.of(
                new Segment("a", 50), new Segment("b", 30), new Segment("c", 20)));

        assertThat(html).contains(
                "<rect class=\"seg s1\" x=\"20.0\" width=\"298.0\"",
                "<rect class=\"seg s2\" x=\"320.0\" width=\"178.8\"",
                "<rect class=\"seg s3\" x=\"500.8\" width=\"119.2\"",
                "<span class=\"key s1\"></span>a 50 (50.0%)",
                "<span class=\"key s3\"></span>c 20 (20.0%)");
    }

    @Test
    void moreThanEightPartsFoldTheRestIntoOther() {
        List<Segment> parts = IntStream.range(0, 10).mapToObj(i -> new Segment("p" + i, 10)).toList();

        assertThat(StackedChart.render("Managers", parts))
                .contains("<span class=\"key s8\"></span>other 30 (30.0%)").doesNotContain("p9");
    }

    @Test
    void eightPartsOrFewerAreKeptAsTheyAre() {
        List<Segment> parts = IntStream.range(0, 8).mapToObj(i -> new Segment("p" + i, 1)).toList();

        assertThat(StackedChart.folded(parts)).isEqualTo(parts);
    }

    @Test
    void eachShareIsItsCountOverTheTotalAndZeroWhenTheTotalIsZero() {
        assertThat(StackedChart.shares(List.of(new Segment("a", 1), new Segment("b", 3)))).containsExactly(0.25, 0.75);
        assertThat(StackedChart.shares(List.of(new Segment("a", 0)))).containsExactly(0.0);
    }

    @Test
    void eachPartStartsAfterThePreviousPartAndAGap() {
        assertThat(StackedChart.starts(List.of(10.0, 20.0, 5.0))).containsExactly(20.0, 32.0, 54.0);
    }
}
