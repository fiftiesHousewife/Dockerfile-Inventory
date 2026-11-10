package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.entry;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.image;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.plain;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OverviewPageTest {

    private static final EstateFigures FIGURES = EstateFigures.of(List.of(
            entry("aaa", "payments/api", "ROOT", List.of("80"), "full", 45, 2, List.of(), List.of("VOLUME"),
                    List.of(new Runtimes.Found("java", "final", "base")),
                    List.of(image("sha256:app", step("sha256:old", "not published since 2024-01-01")))),
            plain("bbb", "platform/base")));

    @Test
    void theEstatesSummaryShowsItsCountsSharesAndTalliesAndLinksTheOtherPages() {
        String body = OverviewPage.body(FIGURES, List.of("payments", "platform"), "", null);

        assertAll(
                () -> assertThat(body).contains("<div class=\"value\">2</div><div class=\"label\">Dockerfiles"),
                () -> assertThat(body).contains("Runs as root", "Exposes a port below 1024", "Ships a toolchain"),
                () -> assertThat(body).contains("java (shipped)", "VOLUME", "over 40"),
                () -> assertThat(body).contains("not published for a while"),
                () -> assertThat(body).contains("href=\"hierarchy.html\"", "href=\"blocked.html\"",
                        "href=\"archetypes.html\"", "href=\"orgs/payments.html\"",
                        "href=\"catalogue/dockerfiles.jsonl\""));
    }

    @Test
    void anOrganisationsSummaryLinksUpToTheEstatesPages() {
        assertThat(OverviewPage.body(FIGURES, List.of(), "../", null)).contains("href=\"../blocked.html\"")
                .doesNotContain("href=\"orgs/");
    }

    @Test
    void theSummarysRowsLinkToTheirLists() {
        assertThat(OverviewPage.body(FIGURES, List.of(), "", "lists/"))
                .contains("<a href=\"lists/runtime/java-shipped.html\">java (shipped)</a>",
                        "<a href=\"lists/final-image/runs-as-root.html\">Runs as root</a>");
    }

    @Test
    void theFinalImagesSharesComeLargestFirst() {
        String shares = OverviewPage.shares(new EstateFigures(10, 0, 10, 0, 1, Map.of(), Map.of(),
                0, 2, 1, 7, Map.of(), Map.of(), Map.of(), Map.of(), 0), null);

        assertThat(List.of(shares.indexOf("Ships a toolchain"), shares.indexOf("Runs as root"),
                shares.indexOf("Exposes a port below 1024"))).isSorted();
    }

    @Test
    void aShareOfNoDockerfilesLinksToNoList() {
        String shares = OverviewPage.shares(new EstateFigures(10, 0, 10, 0, 1, Map.of(), Map.of(),
                0, 2, 0, 7, Map.of(), Map.of(), Map.of(), Map.of(), 0), "lists/final-image/");

        assertAll(
                () -> assertThat(shares).contains("<td>Exposes a port below 1024</td>"),
                () -> assertThat(shares).contains("<a href=\"lists/final-image/runs-as-root.html\">"));
    }

    @Test
    void eachHeadlineFigureLinksToWhereItIsShownInFull() {
        String tiles = OverviewPage.tiles(FIGURES, List.of("payments", "platform"), "");

        assertThat(tiles).contains("<a class=\"tile\" href=\"archetypes.html\"><div class=\"value\">2</div>",
                "<a class=\"tile\" href=\"hierarchy.html\">", "<a class=\"tile\" href=\"#organisations\">",
                "<a class=\"tile\" href=\"unparsed.html\">");
    }

    @Test
    void withoutOrganisationsTheOrganisationsFigureLinksToTheEstatesSummary() {
        assertThat(OverviewPage.tiles(FIGURES, List.of(), "../"))
                .contains("<a class=\"tile\" href=\"../index.html\">");
    }

    @Test
    void theChainsSectionTalliesDepthsAndLayersAndLinksTheHierarchy() {
        assertThat(OverviewPage.chains(FIGURES, "../", "lists/")).contains("<th>Estate images beneath it</th>",
                "<th>Layers</th>", "<a href=\"../hierarchy.html\">The hierarchy</a>");
    }

    @Test
    void theMoreSectionListsTheArchetypesEachOrganisationAndTheDataFiles() {
        assertThat(OverviewPage.more(List.of("payments"), "")).isEqualTo("<h2>More</h2><ul id=\"organisations\">"
                + "<li><a href=\"archetypes.html\">Archetypes</a></li><li><a href=\"orgs/payments.html\">payments</a>"
                + "</li><li><a href=\"catalogue/dockerfiles.jsonl\">catalogue/dockerfiles.jsonl</a></li>"
                + "<li><a href=\"archetypes.jsonl\">archetypes.jsonl</a></li>"
                + "<li><a href=\"passes.json\">passes.json</a></li></ul>");
    }

    @Test
    void aSectionsListsSitInTheirOwnFolderUnderTheLists() {
        assertThat(OverviewPage.in("lists/", "depth")).isEqualTo("lists/depth/");
    }

    @Test
    void withoutListsASectionHasNoFolder() {
        assertThat(OverviewPage.in(null, "depth")).isNull();
    }

    @Test
    void aLabelWithAListLinksToItsPageAndOneWithoutIsEscapedText() {
        assertAll(
                () -> assertThat(OverviewPage.labelled("go (build only)", "lists/runtime/"))
                        .isEqualTo("<a href=\"lists/runtime/go-build-only.html\">go (build only)</a>"),
                () -> assertThat(OverviewPage.labelled("<none>", null)).isEqualTo("&lt;none&gt;"));
    }

    @Test
    void aTallysMetersAreScaledToItsLargestCount() {
        assertThat(OverviewPage.table("Step", Map.of("VOLUME", 4), null)).contains("<td>VOLUME</td>",
                "<td class=\"num\">4</td>", "width:100.0%");
    }
}
