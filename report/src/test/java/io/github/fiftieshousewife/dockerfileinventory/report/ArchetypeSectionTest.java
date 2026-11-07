package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ArchetypeSectionTest {

    private static final ArchetypeCards.Card CARD = new ArchetypeCards.Card(1, "Builds on ubi9.", 2, 3,
            List.of("org/a"), "org/a: Dockerfile", "FROM <ubi9>");

    @Test
    void aCardShowsItsRankSentenceSizeAndExample() {
        assertThat(ArchetypeSection.card(CARD)).isEqualTo("<div class=\"card\"><div class=\"rank\">1</div>"
                + "<p>Builds on ubi9.</p><p class=\"note\">2 Dockerfiles · 3 locations · 1 repositories: org/a</p>"
                + "<details><summary>Example: org/a: Dockerfile</summary><pre>FROM &lt;ubi9&gt;</pre></details>"
                + "</div>");
    }

    @Test
    void fiveRepositoriesAreNamedAndTheRestCounted() {
        List<String> repositories = IntStream.range(0, 7).mapToObj(i -> "r" + i).toList();

        assertThat(ArchetypeSection.repositories(repositories)).isEqualTo("r0, r1, r2, r3, r4 and 2 more");
        assertThat(ArchetypeSection.repositories(List.of("r0"))).isEqualTo("r0");
    }

    @Test
    void aCardWithoutExampleTextHasNoExample() {
        assertThat(ArchetypeSection.example(new ArchetypeCards.Card(1, "s", 1, 1, List.of(), null, null))).isEmpty();
    }

    @Test
    void theSectionPutsTheCoverageCurveBeforeTheCards() {
        String section = ArchetypeSection.render(DashboardTest.SUMMARY, CoverageChartTest.ranking(1),
                List.of(CARD));

        assertThat(section.indexOf("<svg")).isLessThan(section.indexOf("<div class=\"card\">"));
    }
}
