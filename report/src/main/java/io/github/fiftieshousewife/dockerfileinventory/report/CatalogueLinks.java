package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

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
        return of(entries, lineageDir, LinkRule.DEFAULT);
    }

    static Map<String, Links> of(List<CatalogueEntry> entries, Path lineageDir, LinkRule rule) {
        Map<String, List<Link>> parents = new HashMap<>();
        Map<String, List<Link>> children = new HashMap<>();
        List<Pair> pairs = new ArrayList<>(lineageDir == null ? List.of() : lineage(lineageDir, rule));
        Set<String> linked = new HashSet<>();
        pairs.forEach(pair -> linked.add(pair.child()));
        inferred(entries).stream().filter(pair -> !linked.contains(pair.child())).forEach(pairs::add);
        pairs.forEach(pair -> {
            parents.computeIfAbsent(pair.child(), key -> new ArrayList<>())
                    .add(new Link(pair.parent(), pair.source(), pair.evidence()));
            children.computeIfAbsent(pair.parent(), key -> new ArrayList<>())
                    .add(new Link(pair.child(), pair.source(), pair.evidence()));
        });
        Map<String, Links> links = new LinkedHashMap<>();
        entries.forEach(entry -> links.put(entry.blobSha(), new Links(parents.getOrDefault(entry.blobSha(), List.of()),
                children.getOrDefault(entry.blobSha(), List.of()), depth(entry.blobSha(), parents))));
        return links;
    }

    static List<Pair> lineage(Path lineageDir, LinkRule rule) {
        Map<String, List<String>> blobs = new HashMap<>();
        RegistryImages.links(lineageDir.resolve("links.jsonl"), rule).forEach((digest, linked) ->
                blobs.put(digest, linked.built()));
        List<Pair> pairs = new ArrayList<>();
        for (JsonNode row : JsonLines.read(lineageDir.resolve("lineage.jsonl"))) {
            String child = row.path("configDigest").asString();
            String parent = row.path("parent").path("configDigest").stringValue(null);
            if (parent != null && "Of".equals(row.path("parent").path("kind").asString())) {
                String evidence = "image " + child + " is built on image " + parent;
                blobs.getOrDefault(child, List.of()).forEach(c -> blobs.getOrDefault(parent, List.of())
                        .forEach(p -> pairs.add(new Pair(c, p, "lineage", evidence))));
            }
        }
        return pairs;
    }

    record Named(String blobSha, String label) {
    }

    static List<Pair> inferred(List<CatalogueEntry> entries) {
        Map<String, Named> named = new HashMap<>();
        entries.forEach(entry -> NAME_LABELS.forEach(label -> {
            String value = entry.image().labels().get(label);
            if (value != null && !value.isBlank()) {
                named.putIfAbsent(path(value), new Named(entry.blobSha(), label));
            }
        }));
        List<Pair> pairs = new ArrayList<>();
        entries.forEach(entry -> {
            String base = finalBase(entry);
            Named parent = base == null ? null : named.get(path(base));
            if (parent != null && !parent.blobSha().equals(entry.blobSha())) {
                pairs.add(new Pair(entry.blobSha(), parent.blobSha(), "inferred", "base " + path(base)
                        + " is the image " + parent.blobSha() + "'s " + parent.label() + " label says it builds"));
            }
        });
        return pairs;
    }

    static String finalBase(CatalogueEntry entry) {
        CatalogueEntry.StageEntry stage = finalBaseStage(entry);
        return stage == null ? null : stage.baseName();
    }

    static CatalogueEntry.StageEntry finalBaseStage(CatalogueEntry entry) {
        List<CatalogueEntry.StageEntry> stages = entry.build().stages();
        CatalogueEntry.StageEntry stage = stages.isEmpty() ? null : stages.getLast();
        Set<String> seen = new HashSet<>();
        while (stage != null && stage.baseName() == null && stage.fromStage() != null && seen.add(stage.fromStage())) {
            String name = stage.fromStage();
            stage = stages.stream().filter(s -> name.equals(s.name())).findFirst().orElse(null);
        }
        return stage == null || stage.baseName() == null ? null : stage;
    }

    static int depth(String sha, Map<String, List<Link>> parents) {
        Set<String> seen = new HashSet<>(Set.of(sha));
        int depth = 0;
        for (List<Link> up = parents.get(sha); up != null && !up.isEmpty() && seen.add(up.getFirst().blobSha());
                up = parents.get(up.getFirst().blobSha())) {
            depth++;
        }
        return depth;
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
