package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LineagePageTest {

    @Test
    void theTreeIsGroupedUnderEachRootInAFoldedSubtreeWithTheSharedRepositoryNamedOnce() {
        assertThat(LineagePage.tree(LineageSummaryTest.OKD))
                .startsWith("<p class=\"note\">Every image is in docker-local; the name leaves it out.</p>")
                .contains("<details open><summary><strong>base:1</strong> <span class=\"tier\">no Dockerfile</span>"
                        + " &#x2013; 4 images built on it</summary>")
                .contains("<li><strong>cli:1</strong> <span class=\"tier\">weak</span>"
                        + "<br><span class=\"note\">org/cli: Dockerfile</span></li>");
    }

    @Test
    void outdatedImagesAndGapsAreMarkedInPlaceWithAWord() {
        assertThat(LineagePage.tree(LineageSummaryTest.OKD))
                .contains("<strong>app:1</strong> <span class=\"tier\">weak</span> <span class=\"flag\">"
                        + "<span aria-hidden=\"true\">!</span> outdated base: docker-local/base:2 is newer</span>")
                .contains("<strong>gap:1</strong> <span class=\"tier\">no Dockerfile</span> <span class=\"flag\">"
                        + "<span aria-hidden=\"true\">!</span> gap: built from no Dockerfile discovery found</span>");
    }

    @Test
    void aRootWithManyChildrenStartsFolded() {
        List<LineageView.ImageRow> images = new ArrayList<>(List.of(
                new LineageView.ImageRow("r/base:1", null, "NONE", List.of())));
        IntStream.range(0, 10).forEach(i -> images.add(
                new LineageView.ImageRow("r/app-" + i + ":1", "r/base:1", "WEAK", List.of())));
        LineageView view = new LineageView(LineageSummaryTest.OKD.counts(), images, List.of(), List.of(), List.of());

        assertThat(LineagePage.tree(view)).contains("<details><summary><strong>base:1</strong>");
    }

    @Test
    void thePageHasALegendForTheTiersOrSaysLineageHasNotRun() {
        assertThat(LineagePage.body(Optional.of(LineageSummaryTest.OKD)))
                .contains("<li><strong>weak</strong>: the closest Dockerfile on the same base");
        assertThat(LineagePage.body(Optional.empty())).contains("Lineage has not run");
    }
}
