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
}
