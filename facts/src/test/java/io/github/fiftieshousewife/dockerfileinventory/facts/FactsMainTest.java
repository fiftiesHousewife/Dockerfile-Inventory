package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FactsMainTest {

    @Test
    void aMissingSettingIsNamedInTheError() {
        assertThatIllegalStateException().isThrownBy(() -> FactsMain.required(Map.of(), "FACTS_MAIN_TEST_UNSET"))
                .withMessage("FACTS_MAIN_TEST_UNSET is not set");
    }

    @Test
    void aSetSettingIsItsValue() {
        assertThat(FactsMain.required(Map.of("PATH", "/bin"), "PATH")).isEqualTo("/bin");
    }

    @Test
    void theSettingsGivenAreTheOnesReadAndAMissingOneIsRefusedByName() {
        assertThatIllegalStateException().isThrownBy(() -> FactsMain.run(Map.of("DISCOVERY_DIR", "/d")))
                .withMessage("OUT_DIR is not set");
    }

    @Test
    void aRunReadsTheYamlDiscoveryFetchedAfterTheDockerfiles(@TempDir Path dir) throws IOException {
        Path discovery = Files.createDirectories(dir.resolve("discovery"));
        Files.createDirectories(discovery.resolve("blobs"));
        Files.createDirectories(discovery.resolve("yaml"));
        Files.writeString(discovery.resolve("yaml/y1"), "kind: Pod\nmetadata: {name: p}\nspec: {containers: []}\n");
        Files.writeString(discovery.resolve("yaml.jsonl"), "{\"repository\":\"a/b\",\"path\":\"p.yaml\","
                + "\"blobSha\":\"y1\"}\n");

        FactsMain.run(Map.of("DISCOVERY_DIR", discovery.toString(), "OUT_DIR", dir.resolve("out").toString()));

        assertThat(Files.readAllLines(dir.resolve("out/workloads.jsonl"))).hasSize(1);
    }
}
