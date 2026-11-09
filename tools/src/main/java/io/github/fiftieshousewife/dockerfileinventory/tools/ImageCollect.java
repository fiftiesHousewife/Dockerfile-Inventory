package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Collects real images into the fixture bank from a lineage run: each image's config, the facts of every Dockerfile
 * in the repository its source label names (so the linker must pick the right one among them), and the link lineage
 * made. An existing case is kept as it is. {@code collectImages --args=<run directory>}; setting {@code CORPUS}
 * ({@code testdata/corpus/images}).
 */
public final class ImageCollect {

    static final List<String> INPUTS = List.of("lineage/images.jsonl", "lineage/links.jsonl", "facts/facts.jsonl",
            "discovery/manifest.jsonl");
    static final List<String> SOURCE_LABELS = List.of("io.openshift.build.source-location",
            "org.opencontainers.image.source");

    private final Path run;
    private final Path corpus;

    ImageCollect(Path run, Path corpus) {
        this.run = run;
        this.corpus = corpus;
    }

    /**
     * Collects from the run named by the one argument into {@code CORPUS}.
     *
     * @param args the run directory
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: collectImages --args=<run directory>, e.g. --args=out/okd");
            System.exit(2);
        }
        int written = new ImageCollect(Path.of(args[0]), Path.of(System.getenv().getOrDefault("CORPUS",
                "testdata/corpus/images"))).collect();
        System.out.println("corpus: " + written + " images added");
    }

    int collect() {
        INPUTS.stream().map(run::resolve).filter(path -> !Files.isRegularFile(path)).findFirst().ifPresent(path -> {
            throw new IllegalArgumentException("no " + path);
        });
        Map<String, JsonNode> links = Json.rows(run.resolve("lineage/links.jsonl"))
                .collect(Collectors.toMap(row -> row.path("configDigest").asString(), row -> row, (a, b) -> a));
        int written = 0;
        for (Map.Entry<String, String> image : names().entrySet()) {
            Path config = run.resolve("lineage/configs").resolve(image.getKey().replace(':', '_') + ".json");
            Path dir = corpus.resolve(image.getValue() + "-" + image.getKey().substring(7, 15));
            if (!Files.isDirectory(dir) && Files.isRegularFile(config)) {
                write(dir, image.getValue(), Json.read(config), links.get(image.getKey()));
                written++;
            }
        }
        return written;
    }

    // Each placed image's name, the last two parts of its path; the first the run recorded, when pushed under several.
    Map<String, String> names() {
        Map<String, String> names = new TreeMap<>();
        Json.rows(run.resolve("lineage/images.jsonl")).filter(row -> row.path("configDigest").isString())
                .forEach(row -> {
                    List<String> parts = List.of(row.path("image").asString().split("/"));
                    String name = String.join("-", parts.subList(Math.max(0, parts.size() - 2), parts.size()));
                    names.putIfAbsent(row.path("configDigest").asString(), name);
                });
        return names;
    }

    private void write(Path dir, String name, JsonNode config, JsonNode link) {
        JsonNode labels = config.path("config").path("Labels").isObject() ? config.path("config").path("Labels")
                : Json.MAPPER.createObjectNode();
        String repository = repository(SOURCE_LABELS.stream().map(label -> labels.path(label).asString(""))
                .filter(value -> !value.isEmpty()).findFirst().orElse(""));
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("config.json"), Json.pretty(trimmed(config, labels)));
            Files.writeString(dir.resolve("dockerfiles.jsonl"), candidates(repository));
            Files.writeString(dir.resolve("expected.json"), Json.pretty(expected(name, repository, link)));
            System.out.println("added " + dir.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ObjectNode trimmed(JsonNode config, JsonNode labels) {
        ObjectNode trimmed = Json.MAPPER.createObjectNode();
        ObjectNode inner = trimmed.putObject("config");
        inner.set("Labels", labels);
        inner.set("User", config.path("config").path("User").isMissingNode() ? null
                : config.path("config").path("User"));
        trimmed.set("rootfs", config.path("rootfs").isMissingNode() ? null : config.path("rootfs"));
        trimmed.set("history", config.path("history").isMissingNode() ? null : config.path("history"));
        return trimmed;
    }

    // The facts of every Dockerfile the repository holds, in the facts' order.
    String candidates(String repository) {
        Set<String> blobs = Json.rows(run.resolve("discovery/manifest.jsonl"))
                .filter(row -> row.path("repository").asString().equals(repository))
                .map(row -> row.path("blobSha").asString()).collect(Collectors.toSet());
        return Json.rows(run.resolve("facts/facts.jsonl")).filter(row -> blobs.contains(row.path("blobSha").asString()))
                .map(row -> {
                    ObjectNode candidate = Json.MAPPER.createObjectNode();
                    candidate.set("blobSha", row.path("blobSha"));
                    candidate.set("steps", row.path("steps"));
                    candidate.set("fromPath", row.path("fromPath"));
                    candidate.put("repository", repository);
                    return candidate.toString() + "\n";
                }).collect(Collectors.joining());
    }

    static ObjectNode expected(String name, String repository, JsonNode link) {
        ObjectNode expected = Json.MAPPER.createObjectNode();
        expected.put("image", name);
        expected.put("sourceRepository", repository);
        if (link != null) {
            expected.set("tier", link.path("tier"));
            expected.set("blobShas", link.path("blobShas"));
            expected.put("credited", link.path("credited").asBoolean(false));
        }
        return expected;
    }

    // The owner and name from a source label's URL: https://host/owner/name/... -> owner/name.
    static String repository(String url) {
        String path = url.replaceFirst("^https?://[^/]+/", "");
        List<String> parts = List.of(path.split("/"));
        return path.isEmpty() ? "" : String.join("/", parts.subList(0, Math.min(2, parts.size())));
    }
}
