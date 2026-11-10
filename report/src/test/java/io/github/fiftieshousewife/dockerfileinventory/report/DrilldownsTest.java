package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DrilldownsTest {

    private static final List<CatalogueEntry> ENTRIES = List.of(
            entry("aaa", "payments/api", "ROOT", List.of(), "full", 4, 0, List.of(), List.of(),
                    List.of(new Runtimes.Found("java", "final", "base")), List.of()),
            entry("bbb", "platform/tool", "NUMERIC", List.of(), "minimal", 4, 0, List.of(), List.of(),
                    List.of(new Runtimes.Found("go", "build", "FROM golang")), List.of()));

    @Test
    void theDockerfilesBehindEachRowAreGroupedBySectionAndLabel() {
        Map<String, Map<String, List<CatalogueEntry>>> groups = Breakdowns.of(ENTRIES);

        assertAll(
                () -> assertThat(groups.get("runtime")).containsOnlyKeys("java (shipped)", "go (build only)"),
                () -> assertThat(groups.get("final-image").get("Runs as root")).extracting(CatalogueEntry::blobSha)
                        .containsExactly("aaa"),
                () -> assertThat(groups.get("depth").get("0")).hasSize(2));
    }

    @Test
    void eachRowHasAPageListingItsDockerfilesLinkedToTheirOwnPages() {
        Map<String, String> pages = Drilldowns.pages("", Breakdowns.of(ENTRIES));

        assertAll(
                () -> assertThat(pages).containsKeys("lists/runtime/java-shipped.html",
                        "lists/runtime/go-build-only.html", "lists/footprint/full.html"),
                () -> assertThat(pages.get("lists/runtime/java-shipped.html")).contains("java (shipped)",
                        "href=\"../../dockerfiles/aaa.html\"", "payments/api").doesNotContain("bbb"));
    }

    @Test
    void anOrganisationsPagesSitUnderItsOwnFolderAndLinkBackUp() {
        Map<String, String> pages = Drilldowns.pages("orgs/payments/", Breakdowns.of(ENTRIES.subList(0, 1)));

        assertThat(pages.get("orgs/payments/lists/runtime/java-shipped.html"))
                .contains("href=\"../../../../dockerfiles/aaa.html\"");
    }

    @Test
    void theSummarysRowsLinkToTheirLists() {
        assertThat(OverviewPage.body(EstateFigures.of(ENTRIES), List.of(), "", "lists/"))
                .contains("<a href=\"lists/runtime/java-shipped.html\">java (shipped)</a>",
                        "<a href=\"lists/final-image/runs-as-root.html\">Runs as root</a>");
    }

    @Test
    void aLabelBecomesAPagesName() {
        assertThat(Drilldowns.slug("go (build only)")).isEqualTo("go-build-only");
    }
}
