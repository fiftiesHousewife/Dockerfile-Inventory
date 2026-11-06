package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Every Dockerfile in the estate, with its facts and locations, read from the earlier stages' files. */
public record Estate(List<Dockerfile> dockerfiles, List<Failure> failures, List<Location> missing) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public Estate {
        dockerfiles = List.copyOf(dockerfiles);
        failures = List.copyOf(failures);
        missing = List.copyOf(missing);
    }

    public static Estate read(Path factsDir, Path discoveryDir, Path lineageDir) {
        Map<String, List<Location>> locations = new HashMap<>();
        lines(discoveryDir.resolve("manifest.jsonl")).forEach(row -> locations
                .computeIfAbsent(row.path("blobSha").asString(), k -> new ArrayList<>())
                .add(new Location(row.path("repository").asString(), row.path("path").asString(),
                        row.path("scope").asString())));
        List<Dockerfile> dockerfiles = lines(factsDir.resolve("facts.jsonl")).stream()
                .map(row -> dockerfile(row, locations.getOrDefault(row.path("blobSha").asString(), List.of())))
                .toList();
        List<Failure> failures = lines(factsDir.resolve("parse-failures.jsonl")).stream()
                .map(row -> new Failure(row.path("blobSha").asString(), row.path("message").asString(),
                        locations.getOrDefault(row.path("blobSha").asString(), List.of())))
                .toList();
        Set<String> accounted = new HashSet<>();
        dockerfiles.forEach(d -> accounted.add(d.blobSha()));
        failures.forEach(f -> accounted.add(f.blobSha()));
        List<Location> missing = locations.entrySet().stream()
                .filter(e -> !accounted.contains(e.getKey()))
                .flatMap(e -> e.getValue().stream())
                .sorted(Comparator.comparing(Location::repository).thenComparing(Location::path))
                .toList();
        return new Estate(dockerfiles, failures, missing);
    }

    private static Dockerfile dockerfile(JsonNode row, List<Location> locations) {
        JsonNode facts = row.path("facts");
        JsonNode base = facts.path("base");
        int finalStage = facts.path("stageCount").asInt() - 1;
        Set<String> packages = new TreeSet<>();
        Set<String> managers = new TreeSet<>();
        facts.path("packageCalls").forEach(staged -> {
            JsonNode call = staged.path("call");
            managers.add(call.path("manager").asString());
            if (staged.path("stage").asInt() == finalStage) {
                call.path("packages").forEach(p -> packages.add(p.asString()));
            }
        });
        return new Dockerfile(row.path("blobSha").asString(), row.path("textHash").asString(),
                row.path("signatureHash").asString(), row.path("signature").asString(),
                new Base(base.path("name").stringValue(null), base.path("tag").stringValue(null),
                        base.path("digest").stringValue(null)),
                packages, managers, facts.path("userKind").asString(), facts.path("runs").asInt(),
                facts.path("opaqueRuns").asInt(), locations);
    }

    static List<JsonNode> lines(Path file) {
        try {
            return Files.exists(file)
                    ? Files.readAllLines(file).stream().filter(l -> !l.isBlank()).map(JSON::readTree).toList()
                    : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
