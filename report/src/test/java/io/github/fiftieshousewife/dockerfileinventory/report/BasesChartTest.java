package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BasesChartTest {

    @Test
    void aBasesBarIsAsLongAsItsLocationsColouredByStatusWithItsPinnedPartHatched() {
        String svg = BasesChart.render(List.of(
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
    void aBaseNameTooLongForItsLabelIsShortenedButItsHoverTextKeepsAllOfIt() {
        String name = "platform/team-with-a-long-name/images/runtime-base";
        String svg = BasesChart.render(List.of(new BaseRow(name, "2026.11", "runtime-base", 3, 0, "unknown", null)));

        assertThat(svg).contains("<title>" + name + ":2026.11: 3 locations")
                .contains(">platform/team-with-a-long-name/images/r…</text>");
    }

    @Test
    void aRowWithoutATagOrPinnedLocationsHasNoColonAndNoHatchedPart() {
        assertThat(BasesChart.row(new BaseRow("scratch", null, "scratch", 2, 0, "unknown", null), 4, 4))
                .contains("<title>scratch: 2 locations, 0 pinned by digest, unknown</title>")
                .contains("<rect class=\"bar unknown\" width=\"165.0\" x=\"270\" y=\"4\"")
                .doesNotContain("class=\"pinned\"");
    }

    @Test
    void aNameIsShortenedToFortyCharactersEndingInAnEllipsis() {
        assertThat(BasesChart.shortened("a".repeat(40))).isEqualTo("a".repeat(40));
        assertThat(BasesChart.shortened("a".repeat(41))).isEqualTo("a".repeat(39) + "…");
    }

    @Test
    void thePinnedWidthIsThePinnedShareOfTheBarAndZeroForABaseWithNoLocations() {
        assertThat(BasesChart.pinnedWidth(new BaseRow("a", null, "a", 4, 1, "unknown", null), 100)).isEqualTo(25.0);
        assertThat(BasesChart.pinnedWidth(new BaseRow("a", null, "a", 0, 0, "unknown", null), 100)).isZero();
    }
}
