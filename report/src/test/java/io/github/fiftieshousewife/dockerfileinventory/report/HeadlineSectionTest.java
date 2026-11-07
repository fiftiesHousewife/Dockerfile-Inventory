package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeadlineSectionTest {

    @Test
    void aMetricShowsItsStateInWordsItsShareAndItsName() {
        assertThat(HeadlineSection.metric(new Health.Metric("Parse failures", 0.02, Health.State.WATCH)))
                .startsWith("<div class=\"health watch\"><div class=\"state\">watch</div><div class=\"value\">2.0%"
                        + "</div><div class=\"label\">Parse failures<span class=\"info\"");
    }

    @Test
    void theHealthStripHoldsOneTilePerMetric() {
        assertThat(HeadlineSection.health(List.of(new Health.Metric("a", 0, Health.State.GOOD),
                new Health.Metric("b", 1, Health.State.ACT))))
                .startsWith("<div class=\"tiles strip\"><div class=\"health good\">")
                .contains("<div class=\"health act\">").endsWith("</div>");
    }

    @Test
    void theTilesCountLocationsEachPassAndTheArchetypes() {
        assertThat(HeadlineSection.tiles(DashboardTest.SUMMARY)).contains(
                "<div class=\"value\">1,234</div><div class=\"label\">Dockerfile locations",
                "<div class=\"value\">900</div><div class=\"label\">Unique files",
                "<div class=\"value\">700</div><div class=\"label\">Unique after normalising text",
                "<div class=\"value\">400</div><div class=\"label\">Unique signatures",
                "<div class=\"value\">350</div><div class=\"label\">Archetypes<",
                "<div class=\"value\">42</div><div class=\"label\">Archetypes covering 80%",
                "<div class=\"value\">311</div><div class=\"label\">Archetypes covering 95%");
    }

    @Test
    void aPassWithoutAPlainNameKeepsItsOwnName() {
        Summary summary = new Summary(1, 0, List.of(new PassCount("other", 1, 1)), 1, 1, 1, 0, 0, 0, 0,
                Map.of(), Map.of(), false, 0, 0, 0);

        assertThat(HeadlineSection.passes(summary)).contains("<tr><td>other</td><td class=\"num\">1</td>");
    }

    @Test
    void theRatesTableListsEachShareAndCount() {
        assertThat(HeadlineSection.rates(DashboardTest.SUMMARY))
                .contains("<tr><td>Parse failures (of distinct files, templates left out)</td>"
                        + "<td class=\"num\">2.0%</td></tr>")
                .contains("<tr><td>Locations whose file was never parsed</td><td class=\"num\">2</td></tr>");
    }
}
