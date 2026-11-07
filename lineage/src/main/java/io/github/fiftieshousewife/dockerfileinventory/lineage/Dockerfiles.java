package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class Dockerfiles {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Dockerfiles() {
    }

    static List<Dockerfile> read(Path factsDir, Path discoveryDir) {
        List<JsonNode> locations = rows(discoveryDir.resolve("manifest.jsonl"));
        Set<String> released = released(locations);
        Map<String, Set<String>> repositories = repositories(locations);
        return rows(factsDir.resolve("facts.jsonl")).stream()
                .map(row -> dockerfile(row, released, repositories))
                .toList();
    }

    static Set<String> released(List<JsonNode> locations) {
        return locations.stream()
                .filter(n -> n.path("scope").asString().equals("RELEASED"))
                .map(n -> n.path("blobSha").asString())
                .collect(Collectors.toSet());
    }

    static Map<String, Set<String>> repositories(List<JsonNode> locations) {
        return locations.stream().collect(Collectors.groupingBy(n -> n.path("blobSha").asString(),
                Collectors.mapping(n -> n.path("repository").asString(), Collectors.toSet())));
    }

    static Dockerfile dockerfile(JsonNode row, Set<String> released, Map<String, Set<String>> repositories) {
        List<Instruction> steps = new ArrayList<>();
        row.path("steps").forEach(s -> steps.add(
                new Instruction(s.path("keyword").asString(), s.path("text").asString())));
        String sha = row.path("blobSha").asString();
        return new Dockerfile(sha, steps, row.path("fromPath").stringValue(null), released.contains(sha),
                repositories.getOrDefault(sha, Set.of()));
    }

    private static List<JsonNode> rows(Path file) {
        return LineageFiles.lines(file).stream().map(JSON::readTree).toList();
    }
}
