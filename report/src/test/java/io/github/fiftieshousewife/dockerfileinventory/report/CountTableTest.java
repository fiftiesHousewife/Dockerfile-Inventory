package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CountTableTest {

    @Test
    void rowsAreLargestFirstWithAMeterScaledToTheLargest() {
        assertThat(CountTable.render("Manager", Map.of("dnf", 10, "apk", 40), Map.of(), 10)).isEqualTo(
                "<div class=\"scroll\"><table><tr><th>Manager</th><th class=\"num\">Locations</th><th></th></tr>"
                        + "<tr><td>apk</td><td class=\"num\">40</td><td>" + Html.meter(1.0) + "</td></tr>"
                        + "<tr><td>dnf</td><td class=\"num\">10</td><td>" + Html.meter(0.25) + "</td></tr>"
                        + "</table></div>");
    }

    @Test
    void anExtraColumnCountsTheOrganisationsUsingEachRowAndTheLimitCutsTheRest() {
        assertThat(CountTable.render("Base family", Map.of("debian", 5, "alpine", 3, "go", 1),
                Map.of("debian", 4, "alpine", 1), 2))
                .contains("<th class=\"num\">Organisations</th>")
                .contains("<tr><td>debian</td><td class=\"num\">5</td><td>" + Html.meter(1.0)
                        + "</td><td class=\"num\">4</td></tr>")
                .doesNotContain("<td>go</td>");
    }

    @Test
    void familiesLeaveScratchAndUnresolvedOutAndSayHowMany() {
        assertThat(CountTable.bases(Map.of("scratch", 46, "unresolved", 9, "debian", 5)))
                .containsExactly(Map.entry("debian", 5));
        assertThat(CountTable.leftOut(Map.of("scratch", 46, "unresolved", 9, "debian", 5)))
                .isEqualTo("<p class=\"note\">Left out: scratch, the empty base, at 46 locations; a base set by "
                        + "a variable that could not be resolved at 9 locations.</p>");
        assertThat(CountTable.leftOut(Map.of("debian", 5))).isEmpty();
    }

    @Test
    void theOrganisationsUsingEachFamilyAreCountedFromTheirFigures() {
        assertThat(CountTable.organisationsUsing(List.of(
                HealthTest.figures("a", Map.of()), AppliesToTest.figures("b", 1, new Figures.Apart(0, 0),
                        Map.of("debian", 1), "b/x", 1, new Figures.Count(0, 1, true), new Figures.Count(0, 1, true)))))
                .containsExactly(Map.entry("debian", 1));
    }
}
