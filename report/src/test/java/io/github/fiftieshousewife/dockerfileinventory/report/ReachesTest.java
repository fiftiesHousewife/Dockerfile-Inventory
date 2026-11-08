package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReachesTest {

    private static CatalogueEntry in(String sha, String base, String name, String... repositories) {
        CatalogueEntry entry = CatalogueLinksTest.entry(sha, base, Map.of("name", name));
        return new CatalogueEntry(sha, List.of(repositories).stream().map(repository -> new CatalogueEntry.Place(
                repository, "Dockerfile", "RELEASED", "c1", null, null)).toList(), false, entry.image(),
                entry.build(), null, null, null, null, null);
    }

    private static CatalogueLinks.Link link(String sha) {
        return new CatalogueLinks.Link(sha, "inferred", "test");
    }

    @Test
    void aFixReachesEveryDockerfileBuiltOnItWithTheirRepositoriesAndLocations() {
        List<CatalogueEntry> entries = List.of(
                in("aaa", "registry.access.redhat.com/ubi9/ubi:9.6", "acme/base", "acme/base"),
                in("bbb", "registry.example.com/acme/base:1", "acme/runtime", "acme/runtime"),
                in("ccc", "registry.example.com/acme/runtime:1", "acme/app", "acme/app", "acme/app-fork"));

        assertThat(Reaches.of("aaa", CatalogueEntries.bySha(entries), CatalogueLinks.of(entries, null), 0))
                .isEqualTo(new Reaches.Reach(2, 3, 3, 0));
    }

    @Test
    void aCycleOfLinksIsNotFollowedTwice() {
        Map<String, CatalogueLinks.Links> links = Map.of(
                "aaa", new CatalogueLinks.Links(List.of(link("bbb")), List.of(link("bbb")), 1),
                "bbb", new CatalogueLinks.Links(List.of(link("aaa")), List.of(link("aaa")), 1));

        assertThat(Reaches.below("aaa", links)).containsExactly("bbb");
    }

    @Test
    void registryImagesBelowAreThoseWhoseChainPassesThroughAnImageBuiltFromIt() {
        assertThat(new Hierarchies(RegistryImages.read(RegistryImagesTest.LINEAGE), 12, 40)
                .imagesBelow("1723284a866651acfdadf451286d13c452181e12")).isEqualTo(3);
    }
}
