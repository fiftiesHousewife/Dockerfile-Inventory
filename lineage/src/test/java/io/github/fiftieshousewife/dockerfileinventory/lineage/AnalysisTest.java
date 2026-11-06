package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AnalysisTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path out;

    @TempDir
    Path facts;

    @TempDir
    Path discovery;

    @BeforeEach
    void estate() throws IOException {
        LineageFiles files = new LineageFiles(out);
        String ubiStep = "{\"created_by\": \"/bin/sh -c #(nop) ADD file:ubi in / \"}";
        String baseStep = "{\"created_by\": \"/bin/sh -c #(nop) USER 1001\", \"comment\": \"FROM registry.access.redhat.com/ubi9:9.8\"}";
        String appStep = "{\"created_by\": \"/bin/sh -c make\", \"comment\": \"FROM registry.example.com/team/base:1\"}";
        String strayStep = "{\"created_by\": \"/bin/sh -c ./unknown.sh\", \"comment\": \"FROM registry.example.com/team/base:1\"}";
        image(files, "team/base", "1", "base", "2026-09-01T00:00:00Z", "1001", List.of("L1"), ubiStep, baseStep);
        image(files, "team/app", "1", "app", "2026-10-01T00:00:00Z", "1001", List.of("L1", "L2"),
                ubiStep, baseStep, appStep);
        image(files, "team/stray", "1", "stray", "2026-10-02T00:00:00Z", "1001", List.of("L1", "L3"),
                ubiStep, baseStep, strayStep);
        image(files, "ubi9", "9.8", "ubi", "2026-08-01T00:00:00Z", null, List.of("U1"), ubiStep);
        files.record(new TagRow("docker-local", "team/arm", "1", Instant.parse("2026-10-03T00:00:00Z"), null,
                Resolved.Reason.NO_AMD64, "docker-local/team/arm:1"));
        Files.writeString(facts.resolve("facts.jsonl"), """
                {"blobSha":"base-df","steps":[{"keyword":"USER","text":"1001"}],"fromPath":"ubi9"}
                {"blobSha":"app-df","steps":[{"keyword":"RUN","text":"make"}],"fromPath":"team/base"}
                """);
        Files.writeString(discovery.resolve("manifest.jsonl"), """
                {"repository":"platform/base","commitSha":"c1","scope":"DEFAULT_BRANCH","path":"Dockerfile","blobSha":"base-df"}
                {"repository":"payments/app","commitSha":"c2","scope":"RELEASED","path":"Dockerfile","blobSha":"app-df"}
                """);
    }

    @Test
    void writesEachImagesParentLinkAndEffectiveProperties() throws IOException {
        new Analysis(out, facts, discovery).run();

        assertThat(byDigest("lineage.jsonl")).satisfies(lineage -> {
            assertThat(lineage.get("app").path("parent").path("configDigest").asString()).isEqualTo("base");
            assertThat(lineage.get("base").path("parent").path("kind").asString()).isEqualTo("Root");
        });
        assertThat(byDigest("links.jsonl")).satisfies(links -> {
            assertThat(links.get("app").path("tier").asString()).isEqualTo("STRONG");
            assertThat(links.get("base").path("tier").asString()).isEqualTo("PROBABLE");
            assertThat(links.get("stray").path("tier").asString()).isEqualTo("NONE");
        });
        JsonNode user = byDigest("effective.jsonl").get("app").path("user");
        assertThat(user.path("value").asString()).isEqualTo("1001");
        assertThat(user.path("setBy").path("kind").asString()).isEqualTo("Image");
        assertThat(user.path("setBy").path("configDigest").asString()).isEqualTo("base");
    }

    @Test
    void writesGapsExternalBasesAndOutdatedBases() throws IOException {
        new Analysis(out, facts, discovery).run();

        assertThat(byDigest("gaps.jsonl")).containsOnlyKeys("stray");
        assertThat(byDigest("external-bases.jsonl")).containsOnlyKeys("ubi");
        assertThat(Files.readAllLines(out.resolve("outdated-bases.jsonl"))).isEmpty();
    }

    private Map<String, JsonNode> byDigest(String file) throws IOException {
        return Files.readAllLines(out.resolve(file)).stream()
                .map(JSON::readTree)
                .collect(Collectors.toMap(n -> n.path("configDigest").asString(), n -> n));
    }

    private static void image(LineageFiles files, String name, String tag, String digest, String pushed, String user,
                              List<String> layers, String... history) {
        files.record(new TagRow("docker-local", name, tag, Instant.parse(pushed), digest, null, null));
        String diffIds = layers.stream().map(l -> "\"" + l + "\"").collect(Collectors.joining(","));
        files.storeConfig(digest, """
                {"config": {"User": %s}, "rootfs": {"type": "layers", "diff_ids": [%s]}, "history": [%s]}
                """.formatted(user == null ? "null" : "\"" + user + "\"", diffIds, String.join(",", history)));
    }
}
