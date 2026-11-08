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
    void aLabelSetByTheExternalBaseNamesItsPath() {
        assertThat(RegistryImages.label("vendor", json(
                "{\"value\":\"Red Hat\",\"setBy\":{\"kind\":\"ExternalBase\",\"path\":\"ubi9/ubi\"}}")))
                .isEqualTo(new ImageLabel("Red Hat", null, "ubi9/ubi"));
    }

    @Test
    void aSecretLabelValueIsHiddenAsItIsRead() {
        assertThat(RegistryImages.label("build.token", json(
                "{\"value\":\"abc123\",\"setBy\":{\"kind\":\"Unknown\"}}")).value()).startsWith("(hidden sha256:");
    }

    @Test
    void anImageLinkedToSeveralDockerfilesCreditsNoneAndNamesThemAsCandidates() {
        RegistryImage nginx = RegistryImages.read(LINEAGE).get(NGINX);

        assertAll(
                () -> assertThat(nginx.blobShas()).isEmpty(),
                () -> assertThat(nginx.candidates()).hasSize(4));
    }

    @Test
    void aWeakLinkIsACandidateOnly() {
        assertThat(RegistryImages.linked(json("{\"tier\":\"WEAK\",\"blobShas\":[\"aaa\"]}")))
                .isEqualTo(new RegistryImages.Linked(List.of(), List.of("aaa")));
    }

    @Test
    void aStrongLinkCreditsEveryDockerfileItNames() {
        assertThat(RegistryImages.linked(json("{\"tier\":\"STRONG\",\"blobShas\":[\"aaa\"]}")))
                .isEqualTo(new RegistryImages.Linked(List.of("aaa"), List.of()));
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
    void anImageNotRebuiltOnItsUpdatedBaseNamesTheNewerBase(@TempDir Path outdated) throws IOException {
        Path file = outdated.resolve("outdated-bases.jsonl");
        Files.writeString(file, "{\"image\":\"" + PYTHON + "\",\"parent\":\"" + S2I_BASE
                + "\",\"imageName\":\"sclorg/s2i-base-c9s\",\"newer\":\"sha256:0b1c\"}\n");

        assertThat(RegistryImages.newerBases(file)).isEqualTo(Map.of(PYTHON, "sha256:0b1c"));
    }

    @Test
    void theNewestPushIsTheRegistrysLatest() {
        assertThat(RegistryImages.newestPush(List.copyOf(RegistryImages.read(LINEAGE).values())))
                .isEqualTo("2026-11-06T18:18:06.098Z");
    }

    private static JsonNode json(String text) {
        return new ObjectMapper().readTree(text);
    }
}
