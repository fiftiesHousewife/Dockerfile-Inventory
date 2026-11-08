package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FootprintTest {

    @Test
    void eachBaseIsClassedByItsName() {
        assertThat(List.of("registry.access.redhat.com/ubi9/ubi-micro", "ubi9/ubi-minimal", "fedora-minimal",
                "busybox", "registry.access.redhat.com/ubi9/ubi", "debian", "ubuntu", "quay.io/centos/centos",
                "gcr.io/distroless/static", "scratch", "alpine", "debian:bookworm-slim",
                "registry.ci.openshift.org/ocp/5.0"))
                .map(Footprint::of).containsExactly("micro", "minimal", "minimal", "minimal", "full", "full", "full",
                        "full", "distroless", "scratch", "alpine", "slim", "unknown");
    }

    @Test
    void aDockerfileOnAnotherEstateImageTakesTheFootprintOfItsChainsRoot() {
        CatalogueEntry root = CatalogueLinksTest.entry("aaa", "registry.access.redhat.com/ubi9/ubi-minimal:9.4",
                Map.of("name", "acme/base"));
        CatalogueEntry leaf = CatalogueLinksTest.entry("bbb", "registry.example.com/acme/base:1",
                Map.of("name", "acme/app"));
        List<CatalogueEntry> entries = List.of(root, leaf);
        Map<String, CatalogueLinks.Links> links = CatalogueLinks.of(entries, null);

        assertThat(Footprint.through("bbb", CatalogueEntries.bySha(entries), links)).isEqualTo(
                new Footprint.Found("minimal", "registry.access.redhat.com/ubi9/ubi-minimal", List.of("aaa")));
    }
}
