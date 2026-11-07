package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.Counts;
import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.ImageRow;
import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.OutdatedRow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class LineageFiles {

    private static final ObjectMapper JSON = new ObjectMapper();

    private LineageFiles() {
    }

    static LineageView read(Path lineageDir, Map<String, List<Location>> locations) {
        Map<String, String> names = names(lineageDir);
        List<ImageRow> images = images(lineageDir, names, parents(lineageDir, names), locations);
        return new LineageView(counts(lineageDir.resolve("rates.json")), images, LineageView.bases(images),
                gaps(lineageDir.resolve("gaps.jsonl"), names),
                outdated(lineageDir.resolve("outdated-bases.jsonl"), names));
    }

    static Map<String, String> parents(Path lineageDir, Map<String, String> names) {
        Map<String, String> parents = new HashMap<>();
        JsonLines.read(lineageDir.resolve("lineage.jsonl")).forEach(row ->
                parents.put(row.path("configDigest").asString(), parent(row.path("parent"), names)));
        return parents;
    }

    static List<ImageRow> images(Path lineageDir, Map<String, String> names, Map<String, String> parents,
                                 Map<String, List<Location>> locations) {
        return JsonLines.read(lineageDir.resolve("links.jsonl")).stream()
                .filter(row -> names.containsKey(row.path("configDigest").asString()))
                .map(row -> new ImageRow(names.get(row.path("configDigest").asString()),
                        parents.get(row.path("configDigest").asString()), row.path("tier").asString(),
                        dockerfiles(row.path("blobShas"), locations)))
                .sorted(Comparator.comparing(ImageRow::name))
                .toList();
    }

    static List<String> gaps(Path file, Map<String, String> names) {
        return JsonLines.read(file).stream()
                .sorted(Comparator.comparing(JsonLines::pushed).reversed())
                .map(row -> name(row.path("configDigest").asString(), names))
                .toList();
    }

    static List<OutdatedRow> outdated(Path file, Map<String, String> names) {
        return JsonLines.read(file).stream()
                .map(row -> new OutdatedRow(name(row.path("image").asString(), names),
                        name(row.path("parent").asString(), names), name(row.path("newer").asString(), names)))
                .sorted(Comparator.comparing(OutdatedRow::image))
                .toList();
    }

    private static String name(String digest, Map<String, String> names) {
        return names.getOrDefault(digest, digest);
    }

    static Counts counts(Path ratesFile) {
        JsonNode rates = Files.exists(ratesFile) ? JSON.readTree(ratesFile.toFile()) : JSON.createObjectNode();
        Map<String, Integer> links = new TreeMap<>();
        rates.path("links").properties().forEach(e -> links.put(e.getKey(), e.getValue().asInt()));
        return new Counts(rates.path("images").asInt(), rates.path("lineageCoverage").asDouble(), links,
                rates.path("gaps").asInt(), rates.path("externalBases").asInt(), rates.path("outdatedBases").asInt());
    }

    static Map<String, String> names(Path lineageDir) {
        Map<String, JsonNode> newest = new HashMap<>();
        JsonLines.read(lineageDir.resolve("images.jsonl")).stream()
                .filter(row -> !row.path("configDigest").isNull())
                .forEach(row -> newest.merge(row.path("configDigest").asString(), row,
                        (a, b) -> JsonLines.pushed(a).isAfter(JsonLines.pushed(b)) ? a : b));
        Map<String, String> names = new HashMap<>();
        newest.forEach((digest, row) -> names.put(digest, row.path("repository").asString() + "/"
                + row.path("image").asString() + ":" + row.path("tag").asString()));
        return names;
    }

    static String parent(JsonNode parent, Map<String, String> names) {
        return switch (parent.path("kind").asString()) {
            case "Of" -> name(parent.path("configDigest").asString(), names);
            case "Ambiguous" -> LineageView.AMBIGUOUS + String.join(", ", StreamSupport
                    .stream(parent.path("configDigests").spliterator(), false)
                    .map(d -> name(d.asString(), names)).toList());
            default -> null;
        };
    }

    static List<String> dockerfiles(JsonNode blobShas, Map<String, List<Location>> locations) {
        return StreamSupport.stream(blobShas.spliterator(), false)
                .flatMap(blob -> locations.getOrDefault(blob.asString(), List.of()).stream())
                .map(l -> l.repository() + ": " + l.path())
                .sorted()
                .toList();
    }
}
