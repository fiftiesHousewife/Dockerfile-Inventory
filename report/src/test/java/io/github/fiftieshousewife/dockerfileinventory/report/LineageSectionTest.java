package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LineageSectionTest {

    private static final LineageView.Counts COUNTS =
            new LineageView.Counts(3, 1.0, Map.of("NONE", 1, "PROBABLE", 2, "EXACT", 4), 0, 0, 0);
    private static final LineageView VIEW = new LineageView(COUNTS, List.of(),
            List.of(new LineageView.BaseUse("r/base:<2>", 3)),
            IntStream.range(0, 21).mapToObj(i -> "r/gap-" + i).toList(),
            List.of(new LineageView.OutdatedRow("r/app", "r/base:1", "r/base:2")));

    @Test
    void withoutLineageTheSectionSaysItHasNotRun() {
        assertThat(LineageSection.render(Optional.empty())).contains("Lineage has not run");
    }

    @Test
    void linkedCountsEveryTierButNone() {
        assertThat(LineageSection.linked(COUNTS)).isEqualTo(6);
    }

    @Test
    void aSmallViewShowsTheTreeAndALargeOneTheLists() {
        assertThat(LineageSection.view(VIEW)).contains("<ul class=\"tree\">").doesNotContain("<h3>");
        LineageView large = new LineageView(COUNTS, IntStream.range(0, 50)
                .mapToObj(i -> new LineageView.ImageRow("r/" + i, null, "NONE", List.of())).toList(),
                List.of(), List.of(), List.of());
        assertThat(LineageSection.view(large)).contains("<h3>").doesNotContain("<ul class=\"tree\">");
    }

    @Test
    void theListsAreCutToTheTopTwentyAndEscaped() {
        assertThat(LineageSection.gaps(VIEW)).contains("<li><code>r/gap-19</code></li>").doesNotContain("r/gap-20");
        assertThat(LineageSection.outdated(VIEW)).isEqualTo("<tr><td class=\"code\">r/app</td>"
                + "<td class=\"code\">r/base:1</td><td class=\"code\">r/base:2</td></tr>");
        assertThat(LineageSection.bases(VIEW))
                .isEqualTo("<tr><td class=\"code\">r/base:&lt;2&gt;</td><td class=\"num\">3</td></tr>");
        assertThat(LineageSection.lists(VIEW)).contains("<h3>No Dockerfile found (top 20 of 21, newest first)</h3>");
    }

    @Test
    void aHeadingSaysTopTwentyOnlyWhenThereAreMore() {
        assertThat(LineageSection.heading("Gaps", 20, "")).isEqualTo("Gaps (20)");
        assertThat(LineageSection.heading("Gaps", 1234, ", newest first"))
                .isEqualTo("Gaps (top 20 of 1,234, newest first)");
    }
}
