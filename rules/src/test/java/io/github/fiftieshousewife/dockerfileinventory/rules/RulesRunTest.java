package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RulesRunTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path discovery;

    @TempDir
    Path out;

    @BeforeEach
    void estate() throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/a"), "FROM ubi9/ubi-minimal:9.4\nRUN curl -s https://x | sh\n");
        Files.writeString(discovery.resolve("blobs/secret"), "FROM ubi9/ubi-minimal:9.4@sha256:" + "a".repeat(64)
                + "\nENV DB_PASSWORD=hunter2\n"
                + "LABEL org.opencontainers.image.revision=1 org.opencontainers.image.source=2\n");
        Files.writeString(discovery.resolve("blobs/bad"), "not a dockerfile\n");
        Files.writeString(discovery.resolve("blobs/half.part"), "FROM ubi9\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), String.join("\n",
                location("payments/api", "a"), location("payments/web", "a"), location("platform/base", "a"),
                location("platform/tools", "secret"), location("legacy/old", "bad")) + "\n");
    }

    @Test
    void writesFindingsPerDockerfileAndSkipsRulesWhoseInputsAreMissing() throws IOException {
        new RulesRun(discovery, null, null, null, out).run();

        List<JsonNode> findings = lines("findings.jsonl");
        assertThat(findings).extracting(f -> f.path("rule").asString() + "@" + f.path("blobSha").asString())
                .contains("R1@a", "S3@a", "R3@a", "S2@secret", "I2@bad")
                .doesNotContain("R1@secret", "R3@secret");
        JsonNode rates = JSON.readTree(out.resolve("rates.json").toFile());
        assertThat(rates.path("skipped")).extracting(JsonNode::asString).containsExactly("R2", "S1", "S4");
        assertThat(rates.path("rules").path("S3").path("findings").asInt()).isEqualTo(1);
        assertThat(rates.path("rules").path("S3").path("locations").asInt()).isEqualTo(3);
    }

    @Test
    void theFindingsTableHasOneRowPerFindingPerLocation() throws IOException {
        new RulesRun(discovery, null, null, null, out).run();

        List<String> csv = Files.readAllLines(out.resolve("tables/findings.csv"));
        assertThat(csv.getFirst())
                .isEqualTo("rule,severity,repository,path,blob_sha,line,message,value_hash,contributing");
        assertThat(csv).filteredOn(line -> line.startsWith("S3,")).hasSize(3);
        assertThat(Files.readString(out.resolve("tables/ddl.sql"))).contains("CREATE TABLE findings");
    }

    @Test
    void samplesSpreadAcrossOrganisationsAndAreTheSameEveryRun() throws IOException {
        new RulesRun(discovery, null, null, null, out).run();
        List<String> first = Files.readAllLines(out.resolve("samples/S3.csv"));
        new RulesRun(discovery, null, null, null, out).run();

        assertThat(Files.readAllLines(out.resolve("samples/S3.csv"))).isEqualTo(first);
        assertThat(first.subList(1, 3)).extracting(line -> line.split(",")[2].split("/")[0])
                .containsExactlyInAnyOrder("payments", "platform");
    }

    @Test
    void noSecretValueAppearsInAnyOutput() throws IOException {
        new RulesRun(discovery, null, null, null, out).run();

        try (Stream<Path> files = Files.walk(out)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("hunter2");
            }
        }
    }

    @Test
    void withLineageOutputTheEffectiveRulesRunOverEveryImage(@TempDir Path lineage) throws IOException {
        Files.writeString(lineage.resolve("effective.jsonl"), """
                {"configDigest":"img","chain":[{"configDigest":"img","link":{"tier":"STRONG","blobShas":["a"]}}],\
                "user":{"value":null,"setBy":{"kind":"Unknown"}}}
                """);
        Files.writeString(lineage.resolve("images.jsonl"), """
                {"repository":"docker-local","image":"payments/api","tag":"1","configDigest":"img"}
                """);

        new RulesRun(discovery, lineage, null, null, out).run();

        assertThat(lines("findings.jsonl")).filteredOn(f -> f.path("rule").asString().equals("S4"))
                .singleElement().satisfies(f -> assertThat(f.path("blobSha").asString()).isEqualTo("a"));
        assertThat(JSON.readTree(out.resolve("rates.json").toFile()).path("skipped"))
                .extracting(JsonNode::asString).containsExactly("R2", "S1");
    }

    private List<JsonNode> lines(String file) throws IOException {
        return Files.readAllLines(out.resolve(file)).stream().map(JSON::readTree).toList();
    }

    private static String location(String repository, String blob) {
        return ("{\"repository\":\"%s\",\"commitSha\":\"c\",\"scope\":\"RELEASED\","
                + "\"path\":\"Dockerfile\",\"blobSha\":\"%s\"}").formatted(repository, blob);
    }
}
