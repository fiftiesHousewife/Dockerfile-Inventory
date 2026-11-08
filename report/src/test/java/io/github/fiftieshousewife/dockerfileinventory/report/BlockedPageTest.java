package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.image;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class BlockedPageTest {

    private static CatalogueEntry building(String sha, Hierarchies.BuiltImage... images) {
        return Entries.entry(sha, "team/" + sha, "NUMERIC", List.of(), "minimal", 10, 1, List.of(), List.of(),
                List.of(), List.of(images));
    }

    @Test
    void theIntermediatesHoldingBackMostImagesComeFirstWithTheirReasons() {
        Hierarchies.Step stale = step("sha256:stale", "not published since 2024-01-01");
        Hierarchies.Step archived = step("sha256:archived", "repository archived");
        String body = BlockedPage.body(List.of(
                building("aaa", image("sha256:one", archived), image("sha256:four", archived)),
                building("bbb", image("sha256:two", stale), image("sha256:three", stale, archived))));

        assertAll(
                () -> assertThat(body).containsSubsequence("sha256:archived:1", "sha256:stale:1"),
                () -> assertThat(body).contains("repository archived", "not published since 2024-01-01"),
                () -> assertThat(body).contains("href=\"dockerfiles/bbb.html\""));
    }

    @Test
    void anImageHeldBackByTheSameIntermediateTwiceCountsOnce() {
        Hierarchies.Step stale = step("sha256:stale", "old");

        assertThat(BlockedPage.blockers(List.of(building("aaa", image("sha256:one", stale)),
                building("bbb", image("sha256:one", stale)))).getFirst().images()).isEqualTo(1);
    }

    @Test
    void anEstateWithNothingBlockedSaysSo() {
        assertThat(BlockedPage.body(List.of(building("aaa")))).contains("No image is held back");
    }
}
