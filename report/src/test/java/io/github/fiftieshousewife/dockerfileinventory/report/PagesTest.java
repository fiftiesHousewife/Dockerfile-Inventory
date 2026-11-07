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

    static final Set<String> DATA = Set.of("archetypes.json", "passes.json", "tables/dockerfiles.csv",
            "tables/locations.csv", "tables/archetypes.csv", "tables/bases.csv", "tables/ddl.sql");
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
                "href=\"tables/locations.csv\"", "href=\"archetypes.json\"", "href=\"tables/ddl.sql\"");
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
}
