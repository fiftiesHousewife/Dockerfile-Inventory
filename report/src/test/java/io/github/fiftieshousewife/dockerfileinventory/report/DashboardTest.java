package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DashboardTest {

    private static final Summary SUMMARY = new Summary(1234, 2,
            List.of(new PassCount("blob", 900, 1234), new PassCount("text-normal", 700, 1234),
                    new PassCount("signature", 400, 1234)),
            350, 42, 311, 0.02, 0.15, 0.25, 0.1, Map.of("microdnf", 800), Map.of("ROOT", 600, "NUMERIC", 634),
            false, 0.9, 3);
    private static final Ranking RANKING = new Ranking(List.of(new Archetype(1, "k", 50, List.of("b"),
            "ubi-minimal:9|<script>alert(1)</script>|git", "b", List.of("org/repo"))), 1234, 42, 311);
    private static final List<BaseRow> BASES = List.of(
            new BaseRow("ubi9/ubi-minimal", "9.4", "ubi-minimal", 700, 200, "supported", null));

    @Test
    void thePageShowsTheSummarysNumbers() {
        String page = Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty());

        assertThat(page).startsWith("<!doctype html>")
                .contains("1,234").contains(">42<").contains(">311<").contains(">350<")
                .contains("ubi9/ubi-minimal").contains("microdnf").contains("25.0%");
    }

    @Test
    void textFromTheEstateIsHtmlEscaped() {
        ArchetypeCards.Card card = new ArchetypeCards.Card(1, "Builds on <script>alert(1)</script>", 1, 1,
                List.of("org/<script>alert(1)</script>"), "org/x: Dockerfile", "FROM <script>alert(1)</script>");

        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(card), BASES, Optional.empty()))
                .doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void theUserPanelSaysWhetherItShowsEffectiveUsersOrUsersAsWritten() {
        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty()))
                .contains("as written in the Dockerfile");
        Summary effective = new Summary(1234, 2, SUMMARY.passes(), 350, 42, 311, 0.02, 0.15, 0.25, 0.1,
                SUMMARY.managers(), SUMMARY.users(), true, 0.9, 3);
        assertThat(Dashboard.render(effective, RANKING, List.of(), BASES, Optional.empty()))
                .contains("effective, from the built images");
    }

    @Test
    void withoutLineageTheLineageSectionSaysItHasNotRun() {
        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty()))
                .contains("<h2>Lineage<span class=\"info\"").contains("Lineage has not run");
    }

    private static final LineageView CHAIN = LineageView.read(LineageViewTest.CHAIN, LineageViewTest.LOCATIONS);

    @Test
    void lineageTilesCountImagesCoverageLinksAndTheThreeLists() {
        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.of(CHAIN))).contains(
                "<div class=\"value\">5</div><div class=\"label\">Images in Artifactory<span class=\"info\"",
                "<div class=\"value\">100.0%</div><div class=\"label\">With a known parent<span class=\"info\"",
                "<div class=\"value\">4</div><div class=\"label\">Linked to a Dockerfile<span class=\"info\"",
                "<div class=\"value\">0</div><div class=\"label\">No Dockerfile found (gaps)<span class=\"info\"",
                "<div class=\"value\">1</div><div class=\"label\">Built outside (external bases)<span class=\"info\"",
                "<div class=\"value\">0</div><div class=\"label\">On an outdated base<span class=\"info\"");
    }

    @Test
    void aSmallRunShowsEveryImageUnderItsParentWithItsDockerfiles() {
        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.of(CHAIN))).contains("""
                <ul class="tree"><li><code>docker-local/centos/centos:stream9</code> · no Dockerfile\
                <ul><li><code>docker-local/sclorg/s2i-core-c9s:c9s</code> · probable: \
                <code>sclorg/s2i-base-container: core/Dockerfile.c9s</code>\
                <ul><li><code>docker-local/sclorg/nginx-container-1-24-c9s:c9s</code> · probable: \
                <code>sclorg/nginx-container: 1.20/Dockerfile.rhel8</code>, \
                <code>sclorg/nginx-container: 1.22/Dockerfile.c9s</code>, \
                <code>sclorg/nginx-container: 1.24/Dockerfile.c8s</code>, \
                <code>sclorg/nginx-container: 1.24/Dockerfile.c9s</code></li>\
                <li><code>docker-local/sclorg/s2i-base-c9s:c9s</code> · probable: \
                <code>sclorg/s2i-base-container: base/Dockerfile.c9s</code>\
                <ul><li><code>docker-local/sclorg/s2i-python-container-3-12-c9s:c9s</code> · probable: \
                <code>sclorg/s2i-python-container: 3.12/Dockerfile.c9s</code></li></ul></li></ul>\
                </li></ul></li></ul>""");
    }

    @Test
    void aLargeRunShowsTheTopTwentyOfEachActionableListInsteadOfTheTree() {
        List<LineageView.ImageRow> images = IntStream.range(0, 60)
                .mapToObj(i -> new LineageView.ImageRow("r/app-" + i + ":1", "r/base:2", "NONE", List.of())).toList();
        List<String> gaps = IntStream.range(0, 25).mapToObj(i -> "r/app-" + i + ":1").toList();
        LineageView large = new LineageView(new LineageView.Counts(61, 1.0, Map.of("NONE", 61), 25, 0, 1), images,
                List.of(new LineageView.BaseUse("r/base:2", 60)), gaps,
                List.of(new LineageView.OutdatedRow("r/app-7:1", "r/base:1", "r/base:2")));

        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.of(large)))
                .doesNotContain("<ul class=\"tree\">")
                .contains("<h3>No Dockerfile found (top 20 of 25, newest first)</h3>")
                .contains("<code>r/app-19:1</code>").doesNotContain("<code>r/app-20:1</code>")
                .contains("<h3>On an outdated base (1)</h3>")
                .contains("<td class=\"code\">r/app-7:1</td><td class=\"code\">r/base:1</td>"
                        + "<td class=\"code\">r/base:2</td>")
                .contains("<h3>Most-used bases (1)</h3>")
                .contains("<td class=\"code\">r/base:2</td><td class=\"num\">60</td>");
    }

    @Test
    void anImageLinkedToManyDockerfilesListsFiveAndCountsTheRest() {
        List<String> files = IntStream.range(0, 8).mapToObj(i -> "org/repo: " + i + "/Dockerfile").toList();
        LineageView view = new LineageView(new LineageView.Counts(1, 1.0, Map.of("PROBABLE", 1), 0, 0, 0),
                List.of(new LineageView.ImageRow("r/app:1", null, "PROBABLE", files)),
                List.of(), List.of(), List.of());

        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.of(view)))
                .contains("<code>org/repo: 4/Dockerfile</code> and 3 more</li>")
                .doesNotContain("org/repo: 5/Dockerfile");
    }

    @Test
    void theHealthStripNamesEachMetricsStateInWordsNotOnlyInColour() {
        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty())).contains(
                "<div class=\"health watch\"><div class=\"state\">watch</div><div class=\"value\">2.0%</div>"
                        + "<div class=\"label\">Parse failures<span class=\"info\"",
                "<div class=\"health act\"><div class=\"state\">act</div><div class=\"value\">10.0%</div>"
                        + "<div class=\"label\">Bases at end of life<span class=\"info\"");
    }

    @Test
    void theCoverageCurveSitsAboveTheArchetypeTable() {
        String page = Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty());

        assertThat(page).contains("<h2>Archetype coverage<span class=\"info\"")
                .contains(Charts.coverage(RANKING));
        assertThat(page.indexOf(Charts.coverage(RANKING))).isLessThan(page.indexOf("<h2>Largest archetypes"));
    }

    @Test
    void theBasesChartHasALegendInWordsAndKeepsItsTableUnderneath() {
        String page = Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty());

        assertThat(page).contains(Charts.bases(BASES))
                .contains("<span class=\"key eol\"></span>end of life",
                        "<span class=\"key supported\"></span>supported",
                        "<span class=\"key unknown\"></span>not on the list", "hatched: pinned by digest")
                .contains("<details><summary>Table</summary><div class=\"scroll\"><table><tr><th>Image</th>");
    }

    @Test
    void usersManagersAndParseOutcomeAreStackedBarsInAFixedOrder() {
        String page = Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty());

        assertThat(page).contains(Charts.stacked("Final user", List.of(new Charts.Segment("root", 600),
                        new Charts.Segment("numeric", 634), new Charts.Segment("named", 0),
                        new Charts.Segment("unresolved", 0))))
                .contains(Charts.stacked("Package managers", List.of(new Charts.Segment("microdnf", 800))))
                .contains(Charts.stacked("Parse outcome", List.of(new Charts.Segment("parsed", 879),
                        new Charts.Segment("templates", 3), new Charts.Segment("failed", 18))));
    }

    @Test
    void thePageLoadsNothingFromOutsideAndEscapesEstateTextInsideItsCharts() {
        List<BaseRow> hostile = List.of(new BaseRow("evil/<script>x</script>", "1", "evil", 5, 0, "unknown", null));

        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), hostile, Optional.of(CHAIN)))
                .doesNotContain("http://", "https://", " src=", "<script>")
                .contains("evil/&lt;script&gt;x&lt;/script&gt;:1</text>");
    }

    @Test
    void tilesAndHealthMetricsCarryAnInfoMarkerSayingWhatTheyMean() {
        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty())).contains(
                "Unique signatures<span class=\"info\" tabindex=\"0\" data-tip=\"Distinct by what the image is:",
                "Parse failures<span class=\"info\" tabindex=\"0\" data-tip=\"Share of distinct Dockerfiles");
    }

    @Test
    void sectionHeadingsCarryAnInfoMarker() {
        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty()))
                .contains("<h2>Archetype coverage<span class=\"info\"")
                .contains("<h2>Top 20 bases<span class=\"info\"");
    }

    @Test
    void whenNoBaseMatchedTheEndOfLifeListTheBasesSectionSaysSoAndHowToGiveOne() {
        List<BaseRow> unmatched = List.of(new BaseRow("centos", "7", "centos", 5, 0, "unknown", null));

        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), unmatched, Optional.empty()))
                .contains("No base matched an end-of-life list")
                .contains("EOL_LIST");
        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty()))
                .doesNotContain("No base matched an end-of-life list");
    }

    @Test
    void howAlikeTheDockerfilesAreIsToldInPlainWords() {
        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(), BASES, Optional.empty()))
                .contains("<h2>How many really different Dockerfiles?<span class=\"info\"")
                .contains("<tr><td>Different files</td><td class=\"num\">900</td>")
                .contains("<tr><td>Still different once comments and spacing are ignored</td>"
                        + "<td class=\"num\">700</td>")
                .contains("<tr><td>Build a different kind of image</td><td class=\"num\">400</td>")
                .contains("<tr><td>Archetypes: families of near-identical images</td><td class=\"num\">350</td>")
                .doesNotContain("Collapse passes");
    }

    @Test
    void theLargestArchetypesAreCardsWithTheirExampleEscapedNotASignatureTable() {
        ArchetypeCards.Card card = new ArchetypeCards.Card(1, "Builds on ubi9, installs no packages.", 2, 3,
                List.of("org/api", "org/app"), "org/app: Dockerfile", "FROM ubi9\nRUN echo <hi>\n");

        assertThat(Dashboard.render(SUMMARY, RANKING, List.of(card), BASES, Optional.empty()))
                .contains("<h2>Largest archetypes<span class=\"info\"")
                .contains("<div class=\"card\"><div class=\"rank\">1</div><p>Builds on ubi9, installs no "
                        + "packages.</p><p class=\"note\">2 Dockerfiles · 3 locations · 2 repositories: org/api, "
                        + "org/app</p><details><summary>Example: org/app: Dockerfile</summary>"
                        + "<pre>FROM ubi9\nRUN echo &lt;hi&gt;\n</pre></details></div>")
                .doesNotContain("<th>Signature</th>");
    }
}
