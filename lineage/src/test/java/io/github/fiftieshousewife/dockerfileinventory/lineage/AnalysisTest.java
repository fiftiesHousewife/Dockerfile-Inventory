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
import tools.jackson.databind.node.ObjectNode;

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
        String ubiStep = step("#(nop) ADD file:ubi in / ", null);
        String baseStep = step("#(nop) USER 1001", "FROM registry.access.redhat.com/ubi9:9.8");
        String appStep = step("make", "FROM registry.example.com/team/base:1");
        String strayStep = step("./unknown.sh", "FROM registry.example.com/team/base:1");
        image(files, "team/base", "1", "base", "2026-09-01T00:00:00Z", "1001", List.of("U1", "L1"), ubiStep, baseStep);
        image(files, "team/app", "1", "app", "2026-10-01T00:00:00Z", "1001", List.of("U1", "L1", "L2"),
                ubiStep, baseStep, appStep);
        image(files, "team/stray", "1", "stray", "2026-10-02T00:00:00Z", "1001", List.of("U1", "L1", "L3"),
                ubiStep, baseStep, strayStep);
        image(files, "ubi9", "9.8", "ubi", "2026-08-01T00:00:00Z", null, List.of("U1"), ubiStep);
        files.record(new TagRow("docker-local", "team/arm", "1", Instant.parse("2026-10-03T00:00:00Z"), null,
                Resolved.Reason.NO_AMD64, "docker-local/team/arm:1"));
        Files.writeString(facts.resolve("facts.jsonl"), """
                {"blobSha":"base-df","steps":[{"keyword":"USER","text":"1001"}],"fromPath":"ubi9"}
                {"blobSha":"app-df","steps":[{"keyword":"RUN","text":"make"}],"fromPath":"team/base"}
                """);
        Files.writeString(discovery.resolve("manifest.jsonl"), """
                {"repository":"platform/base","scope":"DEFAULT_BRANCH","blobSha":"base-df"}
                {"repository":"payments/app","scope":"RELEASED","blobSha":"app-df"}
                """);
    }

    @Test
    void writesEachImagesParentLinkAndEffectiveProperties() throws IOException {
        new Analysis(out, facts, discovery).run();

        assertThat(byDigest("lineage.jsonl")).satisfies(lineage -> {
            assertThat(lineage.get("app").path("parent").path("configDigest").asString()).isEqualTo("base");
            assertThat(lineage.get("base").path("parent").path("configDigest").asString()).isEqualTo("ubi");
            assertThat(lineage.get("ubi").path("parent").path("kind").asString()).isEqualTo("Root");
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

    @Test
    void writesTheRates() throws IOException {
        new Analysis(out, facts, discovery).run();

        JsonNode rates = JSON.readTree(out.resolve("rates.json").toFile());
        assertThat(rates.path("tags").asInt()).isEqualTo(5);
        assertThat(rates.path("images").asInt()).isEqualTo(4);
        assertThat(rates.path("skipped").path("NO_AMD64").asInt()).isEqualTo(1);
        assertThat(rates.path("lineageCoverage").asDouble()).isEqualTo(1.0);
        assertThat(rates.path("ambiguousParents").asInt()).isZero();
        assertThat(rates.path("links").path("STRONG").asInt()).isEqualTo(1);
        assertThat(rates.path("links").path("PROBABLE").asInt()).isEqualTo(1);
        assertThat(rates.path("links").path("NONE").asInt()).isEqualTo(2);
        assertThat(rates.path("parentMissing").asInt()).isZero();
        assertThat(rates.path("onbuildChildren").asInt()).isZero();
        assertThat(rates.path("gaps").asInt()).isEqualTo(1);
        assertThat(rates.path("externalBases").asInt()).isEqualTo(1);
        assertThat(rates.path("outdatedBases").asInt()).isZero();
    }

    @Test
    void thePublicPython311ImageLinksToTheRealDockerfileThatBuiltIt(@TempDir Path real) throws IOException {
        LineageFiles files = new LineageFiles(real);
        for (String name : List.of("ubi9-ubi-minimal", "ubi9-ubi", "ubi9-python-311", "ubi9-nodejs-20")) {
            files.record(new TagRow("redhat", name, "latest", Instant.parse("2026-11-05T00:00:00Z"), name, null, null));
            files.storeConfig(name, Fixtures.history(name + ".json"));
        }
        JsonNode contract = JSON.readTree(Path.of("../testdata/contracts/python-311.steps.json").toFile());
        ObjectNode row = JSON.createObjectNode().put("blobSha", "python-df").put("fromPath", "ubi9/s2i-base");
        row.set("steps", contract.path("steps"));
        Files.writeString(facts.resolve("facts.jsonl"), row + "\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), "");

        new Analysis(real, facts, discovery).run();

        JsonNode link = Files.readAllLines(real.resolve("links.jsonl")).stream().map(JSON::readTree)
                .filter(n -> n.path("configDigest").asString().equals("ubi9-python-311")).findFirst().orElseThrow();
        assertThat(link.path("tier").asString()).isEqualTo("PROBABLE");
        assertThat(link.path("blobShas").get(0).asString()).isEqualTo("python-df");
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
        files.storeConfig(digest, "{\"config\": {\"User\": %s}, \"rootfs\": {\"diff_ids\": [%s]}, \"history\": [%s]}"
                .formatted(user == null ? "null" : "\"" + user + "\"", diffIds, String.join(",", history)));
    }

    private static String step(String createdBy, String comment) {
        return comment == null
                ? "{\"created_by\": \"/bin/sh -c %s\"}".formatted(createdBy)
                : "{\"created_by\": \"/bin/sh -c %s\", \"comment\": \"%s\"}".formatted(createdBy, comment);
    }
}
