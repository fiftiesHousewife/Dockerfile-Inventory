package io.github.fiftieshousewife.dockerfileinventory.report;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class CatalogueLinks {

    record Link(String blobSha, String source, String evidence) {
    }

    record Links(List<Link> parents, List<Link> children, int depth) {

        Links {
            parents = List.copyOf(parents);
            children = List.copyOf(children);
        }
    }

    record Pair(String child, String parent, String source, String evidence) {
    }

    static final List<String> NAME_LABELS = List.of("name", "org.opencontainers.image.ref.name");

    private CatalogueLinks() {
    }

    static Map<String, Links> of(List<CatalogueEntry> entries, Path lineageDir) {
        List<Pair> pairs = pairs(entries, lineageDir);
        Map<String, List<Link>> parents = pairs.stream().collect(groupingBy(Pair::child,
                mapping(pair -> new Link(pair.parent(), pair.source(), pair.evidence()), toList())));
        Map<String, List<Link>> children = pairs.stream().collect(groupingBy(Pair::parent,
                mapping(pair -> new Link(pair.child(), pair.source(), pair.evidence()), toList())));
        Map<String, Links> links = new LinkedHashMap<>();
        entries.forEach(entry -> links.put(entry.blobSha(), new Links(parents.getOrDefault(entry.blobSha(), List.of()),
                children.getOrDefault(entry.blobSha(), List.of()), depth(entry.blobSha(), parents))));
        return links;
    }

    static List<Pair> pairs(List<CatalogueEntry> entries, Path lineageDir) {
        List<Pair> lineage = lineageDir == null ? List.of() : lineage(lineageDir);
        Set<String> linked = lineage.stream().map(Pair::child).collect(toSet());
        return Stream.concat(lineage.stream(),
                inferred(entries).stream().filter(pair -> !linked.contains(pair.child()))).toList();
    }

    static List<Pair> lineage(Path lineageDir) {
        Map<String, List<String>> blobs = LineageFiles.links(lineageDir.resolve("links.jsonl")).entrySet().stream()
                .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().built()));
        return JsonLines.read(lineageDir.resolve("lineage.jsonl")).stream()
                .filter(row -> row.path("parent").path("configDigest").stringValue(null) != null
                        && "Of".equals(row.path("parent").path("kind").asString()))
                .flatMap(row -> lineagePairs(row.path("configDigest").asString(),
                        row.path("parent").path("configDigest").asString(), blobs))
                .toList();
    }

    static Stream<Pair> lineagePairs(String child, String parent, Map<String, List<String>> blobs) {
        String evidence = "image " + child + " is built on image " + parent;
        return blobs.getOrDefault(child, List.of()).stream().flatMap(childSha -> blobs.getOrDefault(parent, List.of())
                .stream().map(parentSha -> new Pair(childSha, parentSha, "lineage", evidence)));
    }

    record Named(String blobSha, String label) {
    }

    static List<Pair> inferred(List<CatalogueEntry> entries) {
        Map<String, Named> named = named(entries);
        return entries.stream().flatMap(entry -> inferred(entry, named).stream()).toList();
    }

    static Map<String, Named> named(List<CatalogueEntry> entries) {
        return entries.stream().flatMap(entry -> NAME_LABELS.stream()
                        .filter(label -> hasText(entry.image().labels().get(label)))
                        .map(label -> Map.entry(path(entry.image().labels().get(label)),
                                new Named(entry.blobSha(), label))))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (first, later) -> first));
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static Optional<Pair> inferred(CatalogueEntry entry, Map<String, Named> named) {
        String base = finalBase(entry);
        Named parent = base == null ? null : named.get(path(base));
        return parent == null || parent.blobSha().equals(entry.blobSha()) ? Optional.empty()
                : Optional.of(new Pair(entry.blobSha(), parent.blobSha(), "inferred", "base " + path(base)
                        + " is the image " + parent.blobSha() + "'s " + parent.label() + " label says it builds"));
    }

    static String finalBase(CatalogueEntry entry) {
        CatalogueEntry.StageEntry stage = finalBaseStage(entry);
        return stage == null ? null : stage.baseName();
    }

    static CatalogueEntry.StageEntry finalBaseStage(CatalogueEntry entry) {
        List<CatalogueEntry.StageEntry> stages = entry.build().stages();
        Set<String> seen = new HashSet<>();
        return Stream.iterate(stages.isEmpty() ? null : stages.getLast(), Objects::nonNull,
                        stage -> stage.fromStage() != null && seen.add(stage.fromStage())
                                ? stageNamed(stages, stage.fromStage()) : null)
                .filter(stage -> stage.baseName() != null).findFirst().orElse(null);
    }

    static CatalogueEntry.StageEntry stageNamed(List<CatalogueEntry.StageEntry> stages, String name) {
        return stages.stream().filter(stage -> name.equals(stage.name())).findFirst().orElse(null);
    }

    static int depth(String sha, Map<String, List<Link>> parents) {
        Set<String> seen = new HashSet<>(Set.of(sha));
        return (int) Stream.iterate(parents.get(sha), up -> up != null && !up.isEmpty()
                && seen.add(up.getFirst().blobSha()), up -> parents.get(up.getFirst().blobSha())).count();
    }

    // Docker's rule: the first segment is a registry host when it has a dot or a port, or is localhost.
    static String path(String image) {
        String name = image.replaceFirst("@.*$", "").replaceFirst(":[^/]*$", "");
        int slash = name.indexOf('/');
        String first = slash < 0 ? "" : name.substring(0, slash);
        boolean host = first.contains(".") || first.contains(":") || first.equals("localhost");
        return host ? name.substring(slash + 1) : name;
    }
}
