package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.entry;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.image;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.plain;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class OverviewPageTest {

    private static final EstateFigures FIGURES = EstateFigures.of(List.of(
            entry("aaa", "payments/api", "ROOT", List.of("80"), "full", 45, 2, List.of(), List.of("VOLUME"),
                    List.of(new Runtimes.Found("java", "final", "base")),
                    List.of(image("sha256:app", step("sha256:old", "not published since 2024-01-01")))),
            plain("bbb", "platform/base")));

    @Test
    void theEstatesSummaryShowsItsCountsSharesAndTalliesAndLinksTheOtherPages() {
        String body = OverviewPage.body(FIGURES, List.of("payments", "platform"), "");

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
        assertThat(OverviewPage.body(FIGURES, List.of(), "../")).contains("href=\"../blocked.html\"")
                .doesNotContain("href=\"orgs/");
    }

    @Test
    void theFinalImagesSharesComeLargestFirst() {
        String shares = OverviewPage.shares(new EstateFigures(10, 0, 10, 0, 1, java.util.Map.of(), java.util.Map.of(),
                0, 2, 1, 7, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), 0));

        assertThat(java.util.List.of(shares.indexOf("Ships a toolchain"), shares.indexOf("Runs as root"),
                shares.indexOf("Exposes a port below 1024"))).isSorted();
    }

    @Test
    void eachHeadlineFigureLinksToWhereItIsShownInFull() {
        String body = OverviewPage.body(FIGURES, List.of("payments", "platform"), "");

        assertThat(body).contains("<a class=\"tile\" href=\"archetypes.html\"><div class=\"value\">2</div>",
                "<a class=\"tile\" href=\"hierarchy.html\">", "<a class=\"tile\" href=\"#organisations\">",
                "<a class=\"tile\" href=\"unparsed.html\">");
    }
}
