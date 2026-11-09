package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// What a run said of each Dockerfile, read from its stages' files.
record RunOutcomes(List<Outcome> failures, List<Outcome> parsed, Map<String, String> roundTrips,
                   Map<String, Location> locations, Set<String> secrets) {

    private static final ObjectMapper JSON = new ObjectMapper();
    static final String TEMPLATE = "a template, not a Dockerfile";

    record Outcome(String blobSha, String parse, int unreadableRuns) {
    }

    record Location(String repository, String path, String commit) {

        String line() {
            return repository + "\t" + path + "\t" + commit;
        }
    }

    static RunOutcomes read(Path run) {
        CorpusCollect.inputs().stream().map(run::resolve).filter(path -> !Files.isRegularFile(path)).findFirst()
                .ifPresent(path -> {
                    throw new IllegalArgumentException("no " + path);
                });
        List<Outcome> failures = rows(run.resolve("facts/parse-failures.jsonl")).map(row -> new Outcome(
                        row.path("blobSha").asString(), row.path("message").asString().startsWith(TEMPLATE) ? "template"
                        : "failed", 0))
                .sorted(Comparator.comparing(Outcome::blobSha)).toList();
        List<Outcome> parsed = rows(run.resolve("facts/facts.jsonl")).map(row -> new Outcome(
                row.path("blobSha").asString(), row.path("retried").asBoolean(false) ? "retried" : "parsed",
                row.path("facts").path("opaqueRuns").asInt(0))).toList();
        Map<String, String> roundTrips = rows(run.resolve("converter/roundtrip.jsonl")).collect(Collectors.toMap(
                row -> row.path("blobSha").asString(), RunOutcomes::roundTrip, (a, b) -> a));
        Set<String> secrets = rows(run.resolve("rules/findings.jsonl"))
                .filter(row -> row.path("rule").asString().equals("S2")).map(row -> row.path("blobSha").asString())
                .collect(Collectors.toSet());
        return new RunOutcomes(failures, parsed, roundTrips, locations(run.resolve("discovery/manifest.jsonl")),
                secrets);
    }

    static String roundTrip(JsonNode row) {
        if (row.path("passed").asBoolean(false)) {
            return "passes";
        }
        return "the Dockerfile does not parse".equals(row.path("difference").stringValue(null)) ? "not-parsed"
                : "fails";
    }

    // Each blob's location that sorts first, so a run collects the same case every time.
    static Map<String, Location> locations(Path manifest) {
        Map<String, Location> first = new HashMap<>();
        rows(manifest).forEach(row -> first.merge(row.path("blobSha").asString(), new Location(
                row.path("repository").asString(), row.path("path").asString(), row.path("commitSha").asString()),
                (a, b) -> a.line().compareTo(b.line()) <= 0 ? a : b));
        return first;
    }

    List<Outcome> byBlob() {
        return Stream.concat(failures.stream(), parsed.stream()).sorted(Comparator.comparing(Outcome::blobSha))
                .toList();
    }

    private static Stream<JsonNode> rows(Path file) {
        try {
            return Files.readAllLines(file).stream().filter(line -> !line.isBlank()).map(JSON::readTree);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
