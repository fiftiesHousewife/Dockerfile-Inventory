package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RegistryImagesTest {

    static final Path LINEAGE = Path.of("src/test/resources/fixtures/lineage-chain");
    static final String PYTHON = "sha256:d071ff9a02f8fe8397d15d922610e9b5209e3a944386d1ca27afd0e240b92fb7";
    static final String NGINX = "sha256:21141cd0cc00c9705217877f7c86efff0a39dbdc75ffb3699d014128c8a80459";
    static final String S2I_BASE = "sha256:bf34d32d7e2f2a462e17e8566513d1ebe89f7e27d992f08d40ad0679b14f9017";

    @Test
    void anImageCarriesItsNamesParentDockerfilesLayersAndTimes() {
        RegistryImage python = RegistryImages.read(LINEAGE).get(PYTHON);

        assertAll(
                () -> assertThat(python.names()).containsExactly("sclorg/s2i-python-container-3-12-c9s:c9s"),
                () -> assertThat(python.parent()).isEqualTo(S2I_BASE),
                () -> assertThat(python.blobShas()).containsExactly("22461d2436a54f83f08d9410d05897f7562a6c23"),
                () -> assertThat(python.layers()).isEqualTo(5),
                () -> assertThat(python.created()).isEqualTo("2026-11-06T18:18:01.408158472Z"),
                () -> assertThat(python.lastPushed()).isEqualTo("2026-11-06T18:18:06.098Z"),
                () -> assertThat(python.newerBase()).isNull());
    }

    @Test
    void anImageCarriesItsLabelsWithTheImageThatSetEach() {
        Map<String, ImageLabel> labels = RegistryImages.read(LINEAGE).get(PYTHON).labels();

        assertAll(
                () -> assertThat(labels.get("name")).isEqualTo(new ImageLabel("sclorg/python-312-c9s", PYTHON, null)),
                () -> assertThat(labels.get("version")).isEqualTo(new ImageLabel("9", S2I_BASE, null)),
                () -> assertThat(labels.get("io.buildah.version")).isEqualTo(new ImageLabel("1.37.0", null, null)));
    }

    @Test
    void anImageLinkedToSeveralDockerfilesCreditsNoneAndNamesThemAsCandidates() {
        RegistryImage nginx = RegistryImages.read(LINEAGE).get(NGINX);

        assertAll(
                () -> assertThat(nginx.blobShas()).isEmpty(),
                () -> assertThat(nginx.candidates()).hasSize(4));
    }

    @Test
    void theRowsOfOneImageAreGroupedByConfigDigestAndUnresolvedOnesLeftOut() {
        assertThat(RegistryImages.rowsByDigest(List.of(json("{\"configDigest\":\"sha256:b\",\"tag\":\"1\"}"),
                json("{\"configDigest\":null,\"tag\":\"2\"}"), json("{\"configDigest\":\"sha256:a\",\"tag\":\"3\"}"),
                json("{\"configDigest\":\"sha256:b\",\"tag\":\"4\"}"))).keySet())
                .containsExactly("sha256:b", "sha256:a");
    }

    @Test
    void eachImagePathsTagsIncludeTheUnresolvedOnes() {
        assertThat(RegistryImages.tagsOfImage(List.of(json("{\"image\":\"a\",\"tag\":\"1\",\"configDigest\":null}"),
                json("{\"image\":\"a\",\"tag\":\"sha256-ab.sig\"}"))))
                .isEqualTo(Map.of("a", Set.of("1", "sha256-ab.sig")));
    }

    @Test
    void anImageReadWithoutAConfigHasUnknownLayersAndNoCreationTime() {
        RegistryImage image = RegistryImages.image("sha256:x", List.of(json("{\"image\":\"a\",\"tag\":\"1\"}")),
                json("{}"), LineageFiles.read(LINEAGE), Map.of());

        assertAll(
                () -> assertThat(image.names()).containsExactly("a:1"),
                () -> assertThat(image.layers()).isNull(),
                () -> assertThat(image.created()).isNull());
    }

    @Test
    void anImageWhoseConfigWasNotReadHasUnknownLayers() {
        assertThat(RegistryImages.layers(json("{}"))).isNull();
    }

    @Test
    void theRootHasNoParent() {
        assertThat(RegistryImages.read(LINEAGE).get(
                "sha256:cdc65e3dc1b675127b1e26ef79884f63cae4c46c00a89f0c18cb5c617bec62c8").parent()).isNull();
    }

    @Test
    void theNewestPushIsTheRegistrysLatest() {
        assertThat(RegistryImages.newestPush(List.copyOf(RegistryImages.read(LINEAGE).values())))
                .isEqualTo("2026-11-06T18:18:06.098Z");
    }

    @Test
    void aConfigIsReadFromTheLineageDirectoryAndIsEmptyWhenAbsent() {
        assertAll(
                () -> assertThat(RegistryImages.config(LINEAGE, PYTHON).path("rootfs").isObject()).isTrue(),
                () -> assertThat(RegistryImages.config(LINEAGE, "sha256:absent").isEmpty()).isTrue());
    }

    @Test
    void theNewestOfSeveralTimesIsTheLatestInstantWithMissingOnesIgnored() {
        assertAll(
                () -> assertThat(RegistryImages.newest(Arrays.asList("2026-01-01T00:00:00Z", null,
                        "2026-11-06T18:18:06.098Z"))).isEqualTo("2026-11-06T18:18:06.098Z"),
                () -> assertThat(RegistryImages.newest(List.of())).isNull());
    }

    @Test
    void eachImageNamesPushesAreReadFromItsTags() {
        assertThat(RegistryImages.pushesByName(LINEAGE)).containsEntry("sclorg/s2i-python-container-3-12-c9s",
                List.of(Instant.parse("2026-11-06T18:18:06.098Z")));
    }

    @Test
    void eachImagePathsTagsAreReadFromTheRegistry() {
        assertThat(RegistryImages.tagsByImage(LINEAGE)).containsEntry("centos/centos", List.of("stream9"));
    }

    private static JsonNode json(String text) {
        return new ObjectMapper().readTree(text);
    }
}
