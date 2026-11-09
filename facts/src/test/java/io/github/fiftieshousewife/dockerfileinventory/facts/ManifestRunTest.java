package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ManifestRunTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path discovery;

    @TempDir
    Path out;

    @BeforeEach
    void discoveredYaml() throws IOException {
        Path yaml = Files.createDirectories(discovery.resolve("yaml"));
        Files.writeString(yaml.resolve("y1"), "kind: Deployment\nmetadata: {name: api}\nspec: {template: {spec: "
                + "{containers: [{name: api, image: quay.io/acme/api:1}]}}}\n---\nkind: Service\n"
                + "metadata: {name: api}\n");
        Files.writeString(yaml.resolve("y2"), "kind: Deployment\nmetadata: {name: {{ .Name }}}\n");
        Files.writeString(yaml.resolve("y3"), "kind: ConfigMap\nmetadata: {name: settings}\n");
        Files.writeString(discovery.resolve("yaml.jsonl"), String.join("\n",
                "{\"repository\":\"acme/api\",\"path\":\"deploy/api.yaml\",\"blobSha\":\"y1\"}",
                "{\"repository\":\"acme/web\",\"path\":\"deploy/api.yaml\",\"blobSha\":\"y1\"}",
                "{\"repository\":\"acme/api\",\"path\":\"chart/deploy.yaml\",\"blobSha\":\"y2\"}",
                "{\"repository\":\"acme/api\",\"path\":\"config/settings.yaml\",\"blobSha\":\"y3\"}") + "\n");
    }

    @Test
    void eachWorkloadIsWrittenOnceWithItsFileAndThatFilesLocations() throws IOException {
        new ManifestRun(discovery, out).run();

        List<String> lines = Files.readAllLines(out.resolve("workloads.jsonl"));
        JsonNode workload = JSON.readTree(lines.getFirst());
        assertAll(
                () -> assertThat(lines).hasSize(1),
                () -> assertThat(workload.path("blobSha").asString()).isEqualTo("y1"),
                () -> assertThat(workload.path("kind").asString()).isEqualTo("Deployment"),
                () -> assertThat(workload.path("pod").path("containers").get(0).path("image").asString())
                        .isEqualTo("quay.io/acme/api:1"),
                () -> assertThat(workload.path("locations")).hasSize(2));
    }

    @Test
    void theRatesCountTheFilesReadThoseWithWorkloadsAndThoseLeftUnread() throws IOException {
        new ManifestRun(discovery, out).run();

        JsonNode rates = JSON.readTree(out.resolve("yaml-rates.json").toFile());
        assertAll(
                () -> assertThat(rates.path("files").asInt()).isEqualTo(3),
                () -> assertThat(rates.path("withWorkloads").asInt()).isEqualTo(1),
                () -> assertThat(rates.path("workloads").asInt()).isEqualTo(1),
                () -> assertThat(rates.path("templates").asInt()).isEqualTo(1),
                () -> assertThat(rates.path("notYaml").asInt()).isZero());
    }

    @Test
    void aDiscoveryWithoutYamlWritesNoWorkloads() throws IOException {
        Files.delete(discovery.resolve("yaml.jsonl"));

        new ManifestRun(discovery, out).run();

        assertThat(Files.readAllLines(out.resolve("workloads.jsonl"))).isEmpty();
    }
}
