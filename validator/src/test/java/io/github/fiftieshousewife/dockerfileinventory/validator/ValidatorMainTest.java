package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidatorMainTest {

    @TempDir
    Path out;

    @Test
    void withoutAProfileTheDefaultIsUsed() {
        ValidatorMain.run(Map.of("FACTS_DIR", "src/test/resources/fixtures", "DISCOVERY_DIR",
                "src/test/resources/fixtures", "OUT_DIR", out.toString()));

        assertThat(out.resolve("action-plan.json")).content().contains("\"openshift-4-restricted-v2\"");
    }

    @Test
    void theLineageDirectoryGivesEachDockerfileTheUserItsRegistryImagesRunAs(@TempDir Path lineage) throws IOException {
        Files.writeString(lineage.resolve("links.jsonl"), "{\"configDigest\":\"sha256:app\",\"blobShas\":"
                + "[\"00b7e9b0f4f07bea7f89b52371b0221dc848d085\"],\"credited\":true}\n");
        Files.writeString(lineage.resolve("effective.jsonl"),
                "{\"configDigest\":\"sha256:app\",\"user\":{\"value\":\"0\",\"setBy\":{\"kind\":\"Unknown\"}}}\n");

        ValidatorMain.run(Map.of("FACTS_DIR", "src/test/resources/fixtures", "DISCOVERY_DIR",
                "src/test/resources/fixtures", "LINEAGE_DIR", lineage.toString(), "OUT_DIR", out.toString()));

        assertThat(out.resolve("actions.jsonl")).content().contains("its registry images run as root");
    }

    @Test
    void theCatalogueDirectoryGroupsAnArchetypesCopiesIntoFixes(@TempDir Path catalogue) throws IOException {
        Files.writeString(catalogue.resolve("archetypes.jsonl"), "{\"key\":\"k\",\"representative\":\"r\","
                + "\"blobShas\":[\"00b7e9b0f4f07bea7f89b52371b0221dc848d085\","
                + "\"0e896df40fd9aba9d2df16e92fd4b92ad3c379a7\"]}\n");
        Files.createDirectories(catalogue.resolve("catalogue"));
        Files.writeString(catalogue.resolve("catalogue/dockerfiles.jsonl"), "");

        ValidatorMain.run(Map.of("FACTS_DIR", "src/test/resources/fixtures", "DISCOVERY_DIR",
                "src/test/resources/fixtures", "CATALOGUE_DIR", catalogue.toString(), "OUT_DIR", out.toString()));

        assertThat(Files.readAllLines(out.resolve("fixes.jsonl")))
                .hasSizeLessThan(Files.readAllLines(out.resolve("actions.jsonl")).size());
    }

    @Test
    void aMissingSettingIsRefusedByName() {
        assertThatThrownBy(() -> ValidatorMain.run(Map.of("OUT_DIR", out.toString())))
                .hasMessage("FACTS_DIR must be set");
    }

    @Test
    void aBadProfileIsRefusedByNameBeforeAnythingIsRead() {
        assertThatThrownBy(() -> ValidatorMain.run(Map.of("OUT_DIR", out.toString(), "PROFILE", "openshift-5")))
                .hasMessageStartingWith("PROFILE openshift-5 is neither");
    }
}
