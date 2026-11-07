package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.Figures.Count;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripSectionTest {

    @Test
    void aCellShowsTheValueTheStateInAWordWithAnIconAndWhatTheNumberMeans() {
        assertThat(StripSection.cell(Health.metric(Health.ROOT, new Count(62, 100, true)), "")).isEqualTo(
                "<div class=\"health act\"><div class=\"label\">Final user root" + Html.info(Health.ROOT)
                        + "</div><div class=\"value\">62.0%</div><div class=\"state\"><span class=\"icon\" "
                        + "aria-hidden=\"true\">&#x2715;</span> act</div></div>");
    }

    @Test
    void aCellWithoutDataSaysNoDataAndWhatWouldSupplyIt() {
        assertThat(StripSection.cell(Health.metric(Health.EOL, new Count(0, 100, false)), ""))
                .contains("<div class=\"health no-data\">", "<div class=\"value\">&#x2013;</div>", "> no data</div>",
                        "<div class=\"who\">Needs an end-of-life list (EOL_LIST).</div>");
        assertThat(StripSection.cell(Health.metric(Health.RELEASED, new Count(0, 100, false)), ""))
                .contains("<div class=\"who\">Needs a release index.</div>");
    }

    @Test
    void underEachCellTheOrganisationContributingMostIsNamed() {
        List<Figures> organisations = List.of(HealthTest.figures("konflux-ci", Map.of(Health.ROOT,
                new Count(200, 220, true))), HealthTest.figures("bitnami", Map.of(Health.ROOT,
                new Count(20, 220, true))));

        assertThat(StripSection.render(HealthTest.figures("estate", Map.of(Health.ROOT, new Count(220, 440, true))),
                organisations)).contains("<div class=\"who\">Most from konflux-ci: 200 of 220.</div>");
    }

    @Test
    void anOrganisationsOwnStripNamesNoContributor() {
        assertThat(StripSection.render(HealthTest.figures("acme", Map.of(Health.ROOT, new Count(2, 10, true))),
                List.of())).startsWith("<div class=\"strip\"><div class=\"health watch\">")
                .doesNotContain("class=\"who\"");
    }
}
