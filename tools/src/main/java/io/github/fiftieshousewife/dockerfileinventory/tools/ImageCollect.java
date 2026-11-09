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

/**
 * Collects real images into the fixture bank from a lineage run: each image's config, the facts of every Dockerfile
 * in the repository its source label names (so the linker must pick the right one among them), and the link lineage
 * made. An existing case is kept as it is. {@code collectImages --args=<run directory>}; setting {@code CORPUS}
 * ({@code testdata/corpus/images}).
 */
public final class ImageCollect {

    static final List<String> INPUTS = List.of("lineage/images.jsonl", "lineage/links.jsonl", "facts/facts.jsonl",
            "discovery/manifest.jsonl");

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
        List<Map.Entry<String, String>> added = names().entrySet().stream()
                .filter(image -> !Files.isDirectory(caseDir(image)) && Files.isRegularFile(config(image.getKey())))
                .toList();
        added.forEach(image -> write(caseDir(image), image.getValue(), Json.read(config(image.getKey())),
                links.get(image.getKey())));
        return added.size();
    }

    private Path config(String digest) {
        return run.resolve("lineage/configs").resolve(digest.replace(':', '_') + ".json");
    }

    private Path caseDir(Map.Entry<String, String> image) {
        return corpus.resolve(image.getValue() + "-" + image.getKey().substring(7, 15));
    }

    Map<String, String> names() {
        return Json.rows(run.resolve("lineage/images.jsonl")).filter(row -> row.path("configDigest").isString())
                .collect(Collectors.toMap(row -> row.path("configDigest").asString(),
                        row -> name(row.path("image").asString()), (first, later) -> first, TreeMap::new));
    }

    static String name(String image) {
        List<String> parts = List.of(image.split("/"));
        return String.join("-", parts.subList(Math.max(0, parts.size() - 2), parts.size()));
    }

    private void write(Path dir, String name, JsonNode config, JsonNode link) {
        JsonNode labels = ImageCase.labels(config);
        String repository = ImageCase.sourceRepository(labels);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("config.json"), Json.pretty(ImageCase.trimmed(config, labels)));
            Files.writeString(dir.resolve("dockerfiles.jsonl"), candidates(repository));
            Files.writeString(dir.resolve("expected.json"), Json.pretty(ImageCase.expected(name, repository, link)));
            System.out.println("added " + dir.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String candidates(String repository) {
        Set<String> blobs = Json.rows(run.resolve("discovery/manifest.jsonl"))
                .filter(row -> row.path("repository").asString().equals(repository))
                .map(row -> row.path("blobSha").asString()).collect(Collectors.toSet());
        return Json.rows(run.resolve("facts/facts.jsonl")).filter(row -> blobs.contains(row.path("blobSha").asString()))
                .map(row -> ImageCase.candidate(row, repository) + "\n").collect(Collectors.joining());
    }
}
