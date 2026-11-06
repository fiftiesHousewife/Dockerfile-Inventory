package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the tree, links, fold and gap lists from the inventory ({@code images.jsonl}, {@code configs/}), the facts
 * ({@code facts.jsonl}) and discovery's {@code manifest.jsonl}, and writes them, one row per image, with the rates.
 * Every file is rewritten from its inputs each run.
 */
public final class Analysis {

    private final Path factsDir;
    private final Path discoveryDir;
    private final LineageFiles files;
    private final ObjectMapper json = new ObjectMapper();

    public Analysis(Path outDir, Path factsDir, Path discoveryDir) {
        this.factsDir = factsDir;
        this.discoveryDir = discoveryDir;
        this.files = new LineageFiles(outDir);
    }

    public void run() {
        List<TagRow> tags = files.tags();
        Map<String, List<Reference>> references = references(tags);
        List<Resolved.Node> images = references.keySet().stream()
                .map(digest -> files.config(digest).flatMap(ImageConfig::parse)
                        .map(config -> new Resolved.Node(digest, config)).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        Map<String, Parent> parents = Lineage.of(images);
        Set<String> imageNames = tags.stream().map(TagRow::image).collect(Collectors.toSet());
        Map<String, Link> links = Links.of(images, parents, dockerfiles(), imageNames);
        Map<String, EffectiveImage> effective = Fold.of(images, parents, links);

        files.write("lineage.jsonl", rows(parents, parent -> nested("parent", parent)));
        files.write("links.jsonl", rows(links, json::valueToTree));
        files.write("effective.jsonl", rows(effective, json::valueToTree));
    }

    /** Every reference to each config digest; skipped tags name none. */
    private static Map<String, List<Reference>> references(List<TagRow> tags) {
        Map<String, List<Reference>> references = new TreeMap<>();
        tags.stream().filter(t -> t.configDigest() != null).forEach(t -> references
                .computeIfAbsent(t.configDigest(), d -> new ArrayList<>())
                .add(new Reference(t.repository(), t.image(), t.tag(), t.lastPushed())));
        return references;
    }

    /** Each parsed Dockerfile's steps and base path, released when any location of it is at a released commit. */
    private List<Dockerfile> dockerfiles() {
        Set<String> released = lines(discoveryDir.resolve("manifest.jsonl")).stream()
                .filter(n -> n.path("scope").asString().equals("RELEASED"))
                .map(n -> n.path("blobSha").asString())
                .collect(Collectors.toSet());
        return lines(factsDir.resolve("facts.jsonl")).stream().map(row -> {
            List<Instruction> steps = new ArrayList<>();
            row.path("steps").forEach(s -> steps.add(
                    new Instruction(s.path("keyword").asString(), s.path("text").asString())));
            String sha = row.path("blobSha").asString();
            return new Dockerfile(sha, steps, row.path("fromPath").stringValue(null), released.contains(sha));
        }).toList();
    }

    private ObjectNode nested(String field, Object value) {
        return json.createObjectNode().set(field, json.valueToTree(value));
    }

    /** One line per image, sorted by config digest, with the digest first and the value's fields after it. */
    private <T> List<String> rows(Map<String, T> byDigest, java.util.function.Function<T, JsonNode> toNode) {
        return new TreeMap<>(byDigest).entrySet().stream().map(e -> {
            ObjectNode row = json.createObjectNode().put("configDigest", e.getKey());
            row.setAll((ObjectNode) toNode.apply(e.getValue()));
            return row.toString();
        }).toList();
    }

    private List<JsonNode> lines(Path file) {
        try {
            return Files.exists(file)
                    ? Files.readAllLines(file).stream().filter(l -> !l.isBlank()).map(json::readTree).toList()
                    : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
