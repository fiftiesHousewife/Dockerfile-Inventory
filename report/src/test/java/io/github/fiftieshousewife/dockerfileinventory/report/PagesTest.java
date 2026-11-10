package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.linked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PagesTest {

    static final Set<String> DATA = Set.copyOf(OverviewPage.DATA);
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"");

    static Map<String, String> pages() {
        CatalogueView view = new CatalogueView(List.of(
                linked("a1", "acme/api", List.of(), List.of("b9")),
                linked("b9", "beta/app", List.of("a1"), List.of()),
                Entries.unparsed("zz", "beta/broken")));
        return Pages.all(view, ArchetypeRows.of(ArchetypeRowsTest.RANKING, ArchetypeRowsTest.ESTATE),
                Optional.of(ActionPlanPageTest.view(1)));
    }

    @Test
    void theSummaryEachOrganisationTheTreesTheBlockedTheArchetypesAndEachDockerfileHaveAPage() {
        assertThat(pages()).containsKeys("index.html", "orgs/acme.html", "orgs/beta.html", "hierarchy.html",
                "blocked.html", "archetypes.html", "dockerfiles/a1.html", "dockerfiles/b9.html", "dockerfiles/zz.html");
    }

    @Test
    void withTheValidatorsPlanTheSummaryLeadsWithItAndLinksToItsPage() {
        Map<String, String> pages = pages();

        assertAll(
                () -> assertThat(pages).containsKey("openshift.html"),
                () -> assertThat(pages.get("index.html")).contains("3 of 5 images would break",
                        "href=\"openshift.html\""));
    }

    @Test
    void withWorkloadsTheSummaryCountsThemAndTheirPageListsThem() {
        Map<String, String> pages = Pages.all(new CatalogueView(List.of()),
                ArchetypeRows.of(ArchetypeRowsTest.RANKING, ArchetypeRowsTest.ESTATE),
                Optional.of(ActionPlanPageTest.WORKLOADS));

        assertAll(
                () -> assertThat(pages.get("index.html")).contains("Of 21 workloads, 15 would be refused admission"),
                () -> assertThat(pages.get("openshift.html")).contains("Workloads, worst first"));
    }

    @Test
    void anArchetypeLinksToTheDockerfilePageOfOneOfItsFiles() {
        assertThat(pages().get("archetypes.html")).contains("href=\"dockerfiles/a1.html\"");
    }

    @Test
    void everyLinkOnEveryPageResolvesToAPageOrADataFile() {
        Map<String, String> pages = pages();
        pages.forEach((path, html) -> {
            Matcher links = HREF.matcher(html);
            while (links.find()) {
                String target = Path.of(path).resolveSibling(links.group(1)).normalize().toString();
                assertThat(pages.containsKey(target) || DATA.contains(target))
                        .as("%s links to %s", path, links.group(1)).isTrue();
            }
        });
    }

    @Test
    void everyPageStandsAloneWithNoScriptAndNothingFetchedFromOutside() {
        assertThat(pages().values()).allSatisfy(html -> assertThat(html)
                .startsWith("<!doctype html>").contains("<style>")
                .doesNotContain("<script", "http://", "https://", " src="));
    }

    @Test
    void theRankingIsSplitIntoPagesLinkedToEachOther() {
        assertAll(
                () -> assertThat(Pages.rankingPath(1)).isEqualTo("archetypes.html"),
                () -> assertThat(Pages.rankingPath(3)).isEqualTo("archetypes-3.html"),
                () -> assertThat(Pages.pager(2, 700, 250)).isEqualTo(
                        "<nav class=\"pager\" aria-label=\"Ranking pages\"><a href=\"archetypes.html\">1 to 250</a>"
                                + "<strong>251 to 500</strong><a href=\"archetypes-3.html\">501 to 700</a></nav>"),
                () -> assertThat(Pages.pager(1, 250, 250)).isEmpty());
    }
}
