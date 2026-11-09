package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

final class Analysis {

    private final Path factsDir;
    private final Path discoveryDir;
    private final LineageFiles files;
    private final LinkRule rule;
    private final ObjectMapper json = new ObjectMapper();

    Analysis(Path outDir, Path factsDir, Path discoveryDir, LinkRule rule) {
        this.factsDir = factsDir;
        this.rule = rule;
        this.discoveryDir = discoveryDir;
        this.files = new LineageFiles(outDir);
    }

    void run() {
        List<TagRow> tags = files.tags();
        Map<String, List<Reference>> references = references(tags);
        List<Resolved.Node> images = images(references.keySet());
        Map<String, Parent> parents = Lineage.of(images);
        Set<String> imageNames = tags.stream().map(TagRow::image).collect(Collectors.toSet());
        Map<String, Link> links = rule.credit(
                Links.of(images, parents, Dockerfiles.read(factsDir, discoveryDir), imageNames));
        Map<String, EffectiveImage> effective = Fold.of(images, parents, links);
        GapReport gaps = Gaps.of(references, parents, links);

        files.write("lineage.jsonl", rows(parents, parent -> nested("parent", parent)));
        files.write("links.jsonl", rows(links, json::valueToTree));
        files.write("effective.jsonl", rows(effective, json::valueToTree));
        files.write("gaps.jsonl", gaps.gaps().stream().map(json::writeValueAsString).toList());
        files.write("external-bases.jsonl", gaps.externalBases().stream().map(json::writeValueAsString).toList());
        files.write("outdated-bases.jsonl", gaps.outdatedBases().stream().map(json::writeValueAsString).toList());
        files.write("rates.json", List.of(json.writerWithDefaultPrettyPrinter().writeValueAsString(
                LineageRates.of(tags, parents.values(), links.values(), effective.values(), gaps))));
    }

    static Map<String, List<Reference>> references(List<TagRow> tags) {
        Map<String, List<Reference>> references = new TreeMap<>();
        tags.stream().filter(t -> t.configDigest() != null).forEach(t -> references
                .computeIfAbsent(t.configDigest(), d -> new ArrayList<>())
                .add(new Reference(t.repository(), t.image(), t.tag(), t.lastPushed())));
        return references;
    }

    List<Resolved.Node> images(Set<String> digests) {
        return digests.stream()
                .map(digest -> files.config(digest).flatMap(ImageConfig::parse)
                        .map(config -> new Resolved.Node(digest, config)).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private ObjectNode nested(String field, Object value) {
        return json.createObjectNode().set(field, json.valueToTree(value));
    }

    <T> List<String> rows(Map<String, T> byDigest, Function<T, JsonNode> toNode) {
        return new TreeMap<>(byDigest).entrySet().stream().map(e -> {
            ObjectNode row = json.createObjectNode().put("configDigest", e.getKey());
            row.setAll((ObjectNode) toNode.apply(e.getValue()));
            return row.toString();
        }).toList();
    }
}
