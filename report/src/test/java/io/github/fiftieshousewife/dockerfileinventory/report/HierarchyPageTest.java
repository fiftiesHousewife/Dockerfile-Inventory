package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.linked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class HierarchyPageTest {

    @Test
    void aTreeStartsAtEachRootAndNestsItsDescendantsLinkingToTheirPages() {
        String body = HierarchyPage.body(List.of(
                linked("ccc", "team/app", List.of("bbb"), List.of()),
                linked("aaa", "platform/base", List.of(), List.of("bbb")),
                linked("bbb", "team/runtime", List.of("aaa"), List.of("ccc"))));

        assertAll(
                () -> assertThat(body).contains("href=\"dockerfiles/aaa.html\"", "href=\"dockerfiles/bbb.html\"",
                        "href=\"dockerfiles/ccc.html\""),
                () -> assertThat(body).containsSubsequence("platform/base", "<ul>", "team/runtime", "<ul>",
                        "team/app"));
    }

    @Test
    void aDockerfileWithNeitherParentNorChildIsCountedNotDrawn() {
        String body = HierarchyPage.body(List.of(linked("aaa", "team/alone", List.of(), List.of())));

        assertAll(
                () -> assertThat(body).doesNotContain("team/alone"),
                () -> assertThat(body).contains("1 Dockerfile stands alone"));
    }

    @Test
    void aCycleIsDrawnOnceAroundAndStops() {
        String body = HierarchyPage.body(List.of(
                linked("root", "platform/base", List.of(), List.of("aaa")),
                linked("aaa", "team/a", List.of("root", "bbb"), List.of("bbb")),
                linked("bbb", "team/b", List.of("aaa"), List.of("aaa"))));

        assertThat(body.split("team/a<", -1)).hasSize(2);
    }

    @Test
    void aDockerfileAtSeveralLocationsIsNamedByItsFirstAndCountsTheRest() {
        CatalogueEntry plain = linked("aaa", "team/a", List.of(), List.of());
        CatalogueEntry twice = new CatalogueEntry("aaa", List.of(plain.locations().getFirst(),
                plain.locations().getFirst()), false, null, null, null, null, null, null, null);

        assertThat(HierarchyPage.rest(twice)).isEqualTo("Dockerfile (and 1 more)");
    }

    @Test
    void aNodeSaysHowManyLayersAndImagesItHas() {
        CatalogueEntry entry = Entries.entry("aaa", "team/a", "NUMERIC", List.of(), "minimal", 12, 0, List.of(),
                List.of(), List.of(), List.of(Entries.image("sha256:app")));

        assertThat(HierarchyPage.detail(entry)).isEqualTo("12 layers, 1 image");
    }

    @Test
    void aNodeSaysWhenItLastChangedAndWhyItIsUnmaintained() {
        CatalogueEntry plain = Entries.plain("aaa", "team/a");
        CatalogueEntry entry = new CatalogueEntry("aaa", List.of(
                new CatalogueEntry.Place("team/a", "Dockerfile", "RELEASED", "c1", "2024-05-01T10:00:00Z", null),
                new CatalogueEntry.Place("team/a", "b/Dockerfile", "RELEASED", "c1", "2025-03-01T10:00:00Z", null)),
                false, plain.image(), plain.build(), plain.links(), plain.footprint(),
                new Hierarchies.Hierarchy(plain.hierarchy().layers(), List.of(), List.of(),
                        List.of("repository archived")), null, null);

        assertThat(HierarchyPage.detail(entry))
                .isEqualTo("10 layers, 0 images, last changed 2025-03-01, unmaintained: repository archived");
    }
}
