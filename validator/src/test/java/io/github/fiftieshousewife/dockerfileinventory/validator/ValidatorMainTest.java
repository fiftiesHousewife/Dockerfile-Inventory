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
