package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FootprintTest {

    @Test
    void aDockerfileOnAnotherEstateImageTakesTheFootprintOfItsChainsRoot() {
        CatalogueEntry root = CatalogueLinksTest.entry("aaa", "registry.access.redhat.com/ubi9/ubi-minimal:9.4",
                Map.of("name", "acme/base"));
        CatalogueEntry leaf = CatalogueLinksTest.entry("bbb", "registry.example.com/acme/base:1",
                Map.of("name", "acme/app"));
        List<CatalogueEntry> entries = List.of(root, leaf);
        Map<String, CatalogueLinks.Links> links = CatalogueLinks.of(entries, null);

        assertThat(Footprint.through("bbb", CatalogueEntries.bySha(entries), links, FootprintRules.defaults())).isEqualTo(
                new Footprint.Found("minimal", "registry.access.redhat.com/ubi9/ubi-minimal", List.of("aaa")));
    }
}
