package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FootprintTest {

    private static final CatalogueEntry ENTRY = CatalogueLinksTest.entry("e", "ubi9/ubi:9", Map.of());

    @Test
    void aDockerfileOnAnotherEstateImageTakesTheFootprintOfItsChainsRoot() {
        CatalogueEntry root = CatalogueLinksTest.entry("aaa", "registry.access.redhat.com/ubi9/ubi-minimal:9.4",
                Map.of("name", "acme/base"));
        CatalogueEntry leaf = CatalogueLinksTest.entry("bbb", "registry.example.com/acme/base:1",
                Map.of("name", "acme/app"));
        List<CatalogueEntry> entries = List.of(root, leaf);
        Map<String, CatalogueLinks.Links> links = CatalogueLinks.of(entries, null);

        assertThat(Footprint.through("bbb", CatalogueEntries.bySha(entries), links, FootprintRules.defaults()))
                .isEqualTo(
                new Footprint.Found("minimal", "registry.access.redhat.com/ubi9/ubi-minimal", List.of("aaa")));
    }

    @Test
    void theAncestorsAreTheEstateDockerfilesAlongTheFirstParents() {
        Map<String, CatalogueLinks.Links> links = Map.of("c", links("b"), "b", links("a"), "a", links("outside"));

        assertThat(Footprint.ancestors("c", Map.of("a", ENTRY, "b", ENTRY, "c", ENTRY), links))
                .containsExactly("b", "a");
    }

    @Test
    void aChainLeadingBackToItselfStopsBeforeTheStart() {
        Map<String, CatalogueLinks.Links> links = Map.of("a", links("b"), "b", links("a"));

        assertThat(Footprint.ancestors("a", Map.of("a", ENTRY, "b", ENTRY), links)).containsExactly("b");
    }

    @Test
    void aDockerfileWithoutLinksOrParentsHasNoFirstParent() {
        assertAll(
                () -> assertThat(Footprint.firstParent(Map.of(), "a")).isNull(),
                () -> assertThat(Footprint.firstParent(Map.of("a", new CatalogueLinks.Links(List.of(), List.of(), 0)),
                        "a")).isNull());
    }

    @Test
    void aDockerfileWithoutABaseHasAnUnknownFootprint() {
        CatalogueEntry unresolved = new CatalogueEntry("x", List.of(), false, ENTRY.image(), new CatalogueEntry.Build(
                List.of(), List.of(), List.of(), 0, 0, 0, Map.of(), List.of()), null, null, null, null, null);

        assertThat(Footprint.through("x", Map.of("x", unresolved), Map.of(), FootprintRules.defaults()).footprint())
                .isEqualTo(Footprint.UNKNOWN);
    }

    private static CatalogueLinks.Links links(String parent) {
        return new CatalogueLinks.Links(List.of(new CatalogueLinks.Link(parent, "inferred", "")), List.of(), 1);
    }
}
