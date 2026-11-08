package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogueLinksTest {

    private static final CatalogueEntry BASE = entry("aaa", "quay.io/centos/centos:stream9",
            Map.of("name", "sclorg/s2i-core-c9s"));
    private static final CatalogueEntry CORE = entry("bbb", "quay.io/sclorg/s2i-core-c9s:c9s",
            Map.of("name", "sclorg/s2i-base-c9s"));
    private static final CatalogueEntry APP = entry("ccc", "quay.io/sclorg/s2i-base-c9s:c9s",
            Map.of("org.opencontainers.image.ref.name", "payments/app"));

    @Test
    void aBaseThatAnotherDockerfilesLabelNamesIsAnInferredLinkBothWaysWithItsEvidence() {
        Map<String, CatalogueLinks.Links> links = CatalogueLinks.of(List.of(BASE, CORE), null);

        assertAll(
                () -> assertThat(links.get("bbb").parents()).containsExactly(new CatalogueLinks.Link("aaa", "inferred",
                        "base sclorg/s2i-core-c9s is the image aaa's name label says it builds")),
                () -> assertThat(links.get("aaa").children()).extracting(CatalogueLinks.Link::blobSha)
                        .containsExactly("bbb"));
    }

    @Test
    void depthCountsTheEstateDockerfilesAboveAlongTheChain() {
        Map<String, CatalogueLinks.Links> links = CatalogueLinks.of(List.of(BASE, CORE, APP), null);

        assertAll(
                () -> assertThat(links.get("aaa").depth()).isZero(),
                () -> assertThat(links.get("bbb").depth()).isEqualTo(1),
                () -> assertThat(links.get("ccc").depth()).isEqualTo(2));
    }

    @Test
    void aCycleOfInferredLinksIsNotFollowedForever() {
        CatalogueEntry one = entry("one", "example/two", Map.of("name", "example/one"));
        CatalogueEntry two = entry("two", "example/one", Map.of("name", "example/two"));

        assertThat(CatalogueLinks.of(List.of(one, two), null).get("one").depth()).isEqualTo(1);
    }

    @Test
    void aLinkFromLineageIsKeptWithTheImagesThatShowIt(@TempDir Path lineage) throws IOException {
        Files.writeString(lineage.resolve("links.jsonl"), """
                {"configDigest":"sha256:child","tier":"PROBABLE","blobShas":["ccc"],"parentMissing":false}
                {"configDigest":"sha256:parent","tier":"PROBABLE","blobShas":["aaa"],"parentMissing":false}
                """);
        Files.writeString(lineage.resolve("lineage.jsonl"), """
                {"configDigest":"sha256:child","parent":{"kind":"Of","configDigest":"sha256:parent"}}
                """);

        assertThat(CatalogueLinks.of(List.of(BASE, APP), lineage).get("ccc").parents()).containsExactly(
                new CatalogueLinks.Link("aaa", "lineage", "image sha256:child is built on image sha256:parent"));
    }

    @Test
    void anImageNameIsComparedWithoutItsRegistryTagOrDigest() {
        assertAll(
                () -> assertThat(CatalogueLinks.path("quay.io/sclorg/s2i-core-c9s:c9s"))
                        .isEqualTo("sclorg/s2i-core-c9s"),
                () -> assertThat(CatalogueLinks.path("localhost:5000/app@sha256:abc")).isEqualTo("app"),
                () -> assertThat(CatalogueLinks.path("ubi9/python-311")).isEqualTo("ubi9/python-311"));
    }

    @Test
    void aFinalStageBuiltOnAnEarlierOneHasThatStagesBase() {
        CatalogueEntry entry = new CatalogueEntry("x", List.of(), false, APP.image(), new CatalogueEntry.Build(
                List.of(new CatalogueEntry.StageEntry(0, "base", "ubi9/ubi", "9", null, false, null, List.of(), 0,
                                0, 0, 0, 0, List.of(), List.of(), List.of()),
                        new CatalogueEntry.StageEntry(1, null, null, null, null, false, "base", List.of(), 0, 0,
                                0, 0, 0, List.of(), List.of(), List.of())),
                List.of(), List.of(), 0, 0, 0, Map.of(), List.of()), null, null, null, null);

        assertThat(CatalogueLinks.finalBase(entry)).isEqualTo("ubi9/ubi");
    }

    static CatalogueEntry entry(String sha, String base, Map<String, String> labels) {
        String[] nameAndTag = base.split(":(?=[^/]*$)");
        return new CatalogueEntry(sha, List.of(), false,
                new CatalogueEntry.Image(null, "ROOT", List.of(), null, null, null, List.of(), null, List.of(), null,
                        Map.of(), labels),
                new CatalogueEntry.Build(List.of(new CatalogueEntry.StageEntry(0, null, nameAndTag[0],
                        nameAndTag.length > 1 ? nameAndTag[1] : null, null, false, null, List.of(), 0, 0, 0, 0, 0,
                        List.of(), List.of(), List.of())),
                        List.of(), List.of(), 0, 0, 0, Map.of(), List.of()), null, null, null, null);
    }
}
