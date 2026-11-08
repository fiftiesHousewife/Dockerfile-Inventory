package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PagesTest {

    static final Set<String> DATA = Set.of("catalogue/dockerfiles.jsonl", "archetypes.jsonl", "passes.json");
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"");

    static Report report() {
        Estate estate = Estate.read(EstateTest.FIXTURES, EstateTest.FIXTURES, null);
        List<Group> groups = NearDuplicates.of(Passes.signatureGroups(estate));
        Ranking ranking = Archetypes.rank(groups, estate);
        return Report.of(estate, ranking, Passes.exact(estate), groups.size(), EolList.empty(),
                Optional.of(LineageSummaryTest.OKD), EstateTest.FIXTURES);
    }

    @Test
    void theSummaryEachOrganisationTheArchetypesTheBasesAndTheLineageEachHaveAPage() {
        assertThat(Pages.all(report())).containsKeys("index.html", "orgs/payments.html", "orgs/platform.html",
                "archetypes.html", "archetypes/1.html", "bases.html", "lineage.html");
    }

    @Test
    void theSummaryLinksToEveryOtherKindOfPageAndToTheDataFiles() {
        assertThat(Pages.all(report()).get("index.html")).contains("href=\"orgs/payments.html\"",
                "href=\"archetypes.html\"", "href=\"bases.html\"", "href=\"lineage.html\"",
                "href=\"catalogue/dockerfiles.jsonl\"", "href=\"archetypes.jsonl\"", "href=\"passes.json\"");
    }

    @Test
    void everyLinkOnEveryPageResolvesToAPageOrADataFile() {
        Map<String, String> pages = Pages.all(report());
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
        assertThat(Pages.all(report()).values()).allSatisfy(html -> assertThat(html)
                .startsWith("<!doctype html>").contains("<style>")
                .doesNotContain("<script", "http://", "https://", " src="));
    }

    @Test
    void theRankingIsSplitIntoPagesLinkedToEachOther() {
        assertThat(Pages.rankingPath(1)).isEqualTo("archetypes.html");
        assertThat(Pages.rankingPath(3)).isEqualTo("archetypes-3.html");
        assertThat(Pages.pager(2, 700, 250)).isEqualTo("<nav class=\"pager\" aria-label=\"Ranking pages\">"
                + "<a href=\"archetypes.html\">1 to 250</a><strong>251 to 500</strong>"
                + "<a href=\"archetypes-3.html\">501 to 700</a></nav>");
        assertThat(Pages.pager(1, 250, 250)).isEmpty();
    }
}
