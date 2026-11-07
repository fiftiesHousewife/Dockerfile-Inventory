package io.github.fiftieshousewife.dockerfileinventory.report;

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

record Estate(List<Dockerfile> dockerfiles, List<Failure> failures, List<Location> missing,
              Map<String, UserKind> effectiveUsers) {

    Estate {
        dockerfiles = List.copyOf(dockerfiles);
        failures = List.copyOf(failures);
        missing = List.copyOf(missing);
        effectiveUsers = Map.copyOf(effectiveUsers);
    }

    static Estate read(Path factsDir, Path discoveryDir, Path lineageDir) {
        Map<String, List<Location>> locations = locations(discoveryDir.resolve("manifest.jsonl"));
        List<Dockerfile> dockerfiles = JsonLines.read(factsDir.resolve("facts.jsonl")).stream()
                .map(row -> dockerfile(row, locations.getOrDefault(row.path("blobSha").asString(), List.of())))
                .toList();
        List<Failure> failures = JsonLines.read(factsDir.resolve("parse-failures.jsonl")).stream()
                .map(row -> new Failure(row.path("blobSha").asString(), row.path("message").asString(),
                        locations.getOrDefault(row.path("blobSha").asString(), List.of())))
                .toList();
        return new Estate(dockerfiles, failures, missing(locations, dockerfiles, failures),
                lineageDir == null ? Map.of() : EffectiveUsers.read(lineageDir));
    }

    static Map<String, List<Location>> locations(Path manifest) {
        Map<String, List<Location>> locations = new HashMap<>();
        JsonLines.read(manifest).forEach(row -> locations
                .computeIfAbsent(row.path("blobSha").asString(), k -> new ArrayList<>())
                .add(new Location(row.path("repository").asString(), row.path("path").asString(),
                        row.path("scope").asString())));
        return locations;
    }

    static List<Location> missing(Map<String, List<Location>> locations, List<Dockerfile> dockerfiles,
                                  List<Failure> failures) {
        Set<String> accounted = new HashSet<>();
        dockerfiles.forEach(d -> accounted.add(d.blobSha()));
        failures.forEach(f -> accounted.add(f.blobSha()));
        return locations.entrySet().stream()
                .filter(e -> !accounted.contains(e.getKey()))
                .flatMap(e -> e.getValue().stream())
                .sorted(Comparator.comparing(Location::repository).thenComparing(Location::path))
                .toList();
    }

    static Dockerfile dockerfile(JsonNode row, List<Location> locations) {
        JsonNode facts = row.path("facts");
        JsonNode base = facts.path("base");
        Set<String> managers = new TreeSet<>();
        facts.path("packageCalls").forEach(staged -> managers.add(staged.path("call").path("manager").asString()));
        return new Dockerfile(row.path("blobSha").asString(), row.path("textHash").asString(),
                row.path("signatureHash").asString(), row.path("signature").asString(),
                new Base(base.path("name").stringValue(null), base.path("tag").stringValue(null),
                        base.path("digest").stringValue(null)),
                finalPackages(facts), managers, facts.path("userKind").asString(), facts.path("runs").asInt(),
                facts.path("opaqueRuns").asInt(), locations, facts.path("otherShellRuns").asInt());
    }

    static Set<String> finalPackages(JsonNode facts) {
        int finalStage = facts.path("stageCount").asInt() - 1;
        Set<String> packages = new TreeSet<>();
        facts.path("packageCalls").forEach(staged -> {
            if (staged.path("stage").asInt() == finalStage) {
                staged.path("call").path("packages").forEach(p -> packages.add(p.asString()));
            }
        });
        return packages;
    }
}
