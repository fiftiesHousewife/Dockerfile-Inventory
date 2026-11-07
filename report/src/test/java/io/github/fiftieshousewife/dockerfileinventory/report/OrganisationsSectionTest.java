package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrganisationsSectionTest {

    private static final Figures ACME = Figures.of("acme", FiguresTest.ESTATE.within("acme"), EolList.empty());
    private static final Figures BETA = Figures.of("beta", FiguresTest.ESTATE.within("beta"), EolList.empty());

    @Test
    void eachOrganisationIsARowLinkingToItsPageWithItsFigures() {
        assertThat(OrganisationsSection.row(ACME, 8)).startsWith(
                "<tr><td><a href=\"orgs/acme.html\">acme</a></td><td class=\"num\">8</td><td>" + Html.meter(1.0)
                        + "</td><td class=\"num\">4</td><td class=\"num\">3</td><td class=\"num\">0.0%</td>"
                        + "<td class=\"num\">100.0%</td><td class=\"num\">4</td><td>ubi-minimal</td>"
                        + "<td>acme/bundles, 50.0%</td>");
    }

    @Test
    void theFinalUserMixIsOneHundredPercentBarInAFixedOrder() {
        assertThat(OrganisationsSection.mix(Map.of("ROOT", 1, "NUMERIC", 3)))
                .isEqualTo("<span class=\"mix\" role=\"img\" aria-label=\"root 25.0%, numeric 75.0%\">"
                        + "<span class=\"s1\" style=\"width:25.0%\"></span>"
                        + "<span class=\"s2\" style=\"width:75.0%\"></span></span>");
        assertThat(OrganisationsSection.mix(Map.of())).isEmpty();
    }

    @Test
    void theTableHasALegendForTheUserMixAndBarsScaledToTheLargestOrganisation() {
        assertThat(OrganisationsSection.render(List.of(ACME, BETA)))
                .contains("<span class=\"key s1\"></span>root", "<span class=\"key s4\"></span>unresolved")
                .contains("<a href=\"orgs/beta.html\">beta</a></td><td class=\"num\">2</td><td>"
                        + Html.meter(0.25) + "</td>");
    }

    @Test
    void anOrganisationsPageNameIsSafeForAFile() {
        assertThat(OrganisationsSection.path("my org/../x")).isEqualTo("orgs/my_org_.._x.html");
    }
}
