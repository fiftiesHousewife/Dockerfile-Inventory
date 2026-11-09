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

class LineageFilesTest {

    private static final Path LINEAGE = RegistryImagesTest.LINEAGE;
    private static final String PYTHON = RegistryImagesTest.PYTHON;
    private static final String S2I_BASE = RegistryImagesTest.S2I_BASE;
    private static final String ROOT = "sha256:cdc65e3dc1b675127b1e26ef79884f63cae4c46c00a89f0c18cb5c617bec62c8";

    @Test
    void anImageReadFromTheLineageDirectoryHasItsParentLinkAndLabels() {
        LineageFiles files = LineageFiles.read(LINEAGE);

        assertAll(
                () -> assertThat(files.parentOf(PYTHON)).isEqualTo(S2I_BASE),
                () -> assertThat(files.linkOf(PYTHON).built())
                        .containsExactly("22461d2436a54f83f08d9410d05897f7562a6c23"),
                () -> assertThat(files.labelsOf(PYTHON)).containsKey("name"),
                () -> assertThat(files.newerBaseOf(PYTHON)).isNull());
    }

    @Test
    void anImageLineageNeverSawHasNoParentLinkOrLabels() {
        LineageFiles files = LineageFiles.read(LINEAGE);

        assertAll(
                () -> assertThat(files.parentOf("sha256:absent")).isNull(),
                () -> assertThat(files.linkOf("sha256:absent"))
                        .isEqualTo(new LineageFiles.Linked(List.of(), List.of())),
                () -> assertThat(files.labelsOf("sha256:absent")).isEmpty());
    }

    @Test
    void aLabelSetByTheExternalBaseNamesItsPath() {
        assertThat(LineageFiles.label("vendor", json(
                "{\"value\":\"Red Hat\",\"setBy\":{\"kind\":\"ExternalBase\",\"path\":\"ubi9/ubi\"}}")))
                .isEqualTo(new ImageLabel("Red Hat", null, "ubi9/ubi"));
    }

    @Test
    void aLabelSetByAnImageNamesThatImage() {
        assertThat(LineageFiles.label("name", json(
                "{\"value\":\"python\",\"setBy\":{\"kind\":\"Image\",\"configDigest\":\"sha256:a\"}}")))
                .isEqualTo(new ImageLabel("python", "sha256:a", null));
    }

    @Test
    void aSecretLabelValueIsHiddenAsItIsRead() {
        assertThat(LineageFiles.label("build.token", json(
                "{\"value\":\"abc123\",\"setBy\":{\"kind\":\"Unknown\"}}")).value()).startsWith("(hidden sha256:");
    }

    @Test
    void anImagesLabelsKeepTheirOrder() {
        assertThat(LineageFiles.imageLabels(json(
                "{\"b\":{\"value\":\"2\"},\"a\":{\"value\":\"1\"}}")).keySet()).containsExactly("b", "a");
    }

    @Test
    void eachImagesLabelsAreReadFromTheEffectiveImages() {
        assertThat(LineageFiles.labels(LINEAGE.resolve("effective.jsonl")).get(PYTHON).get("version"))
                .isEqualTo(new ImageLabel("9", S2I_BASE, null));
    }

    @Test
    void aLinkLineageCreditedNamesTheDockerfilesTheImageWasBuiltFrom() {
        assertThat(LineageFiles.linked(json("{\"tier\":\"WEAK\",\"blobShas\":[\"aaa\"],\"credited\":true}")))
                .isEqualTo(new LineageFiles.Linked(List.of("aaa"), List.of()));
    }

    @Test
    void aLinkLineageDidNotCreditOrWroteBeforeCreditingNamesCandidatesOnly() {
        assertAll(
                () -> assertThat(LineageFiles.linked(json(
                        "{\"tier\":\"STRONG\",\"blobShas\":[\"aaa\"],\"credited\":false}")))
                        .isEqualTo(new LineageFiles.Linked(List.of(), List.of("aaa"))),
                () -> assertThat(LineageFiles.linked(json("{\"tier\":\"STRONG\",\"blobShas\":[\"aaa\"]}")))
                        .isEqualTo(new LineageFiles.Linked(List.of(), List.of("aaa"))));
    }

    @Test
    void eachImagesLinkIsReadByItsConfigDigest() {
        assertThat(LineageFiles.links(LINEAGE.resolve("links.jsonl")).get(S2I_BASE).built())
                .containsExactly("84d379564c82cd28d8d358b5f94373bb49f0fb70");
    }

    @Test
    void anImageNotRebuiltOnItsUpdatedBaseNamesTheNewerBase(@TempDir Path outdated) throws IOException {
        Path file = outdated.resolve("outdated-bases.jsonl");
        Files.writeString(file, "{\"image\":\"" + PYTHON + "\",\"parent\":\"" + S2I_BASE
                + "\",\"imageName\":\"sclorg/s2i-base-c9s\",\"newer\":\"sha256:0b1c\"}\n");

        assertThat(LineageFiles.newerBases(file)).isEqualTo(Map.of(PYTHON, "sha256:0b1c"));
    }

    @Test
    void eachImagesParentIsReadAndTheRootHasNone() {
        Map<String, String> parents = LineageFiles.parents(LINEAGE.resolve("lineage.jsonl"));

        assertAll(
                () -> assertThat(parents).containsEntry(PYTHON, S2I_BASE),
                () -> assertThat(parents).doesNotContainKey(ROOT));
    }

    private static JsonNode json(String text) {
        return new ObjectMapper().readTree(text);
    }
}
