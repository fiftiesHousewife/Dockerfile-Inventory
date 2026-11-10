package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

/**
 * Several discovery outputs merged into one estate: every JSON Lines file, each line once, and each Dockerfile once
 * by its blob SHA.
 */
public final class Combine {

    static final List<String> FILES = List.of("manifest.jsonl", "repos.jsonl", "changes.jsonl", "blob-findings.jsonl",
            "yaml.jsonl");
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * What the merge holds.
     *
     * @param locations the Dockerfile locations
     * @param repositories the repositories scanned
     * @param blobs the Dockerfiles stored
     * @param repeated repositories found in more than one source, so counted twice
     */
    public record Summary(int locations, int repositories, int blobs, List<String> repeated) {

        /** Copies the list. */
        public Summary {
            repeated = List.copyOf(repeated);
        }
    }

    private Combine() {
    }

    /**
     * Merges the sources into {@code out}.
     *
     * @param sources discovery output directories
     * @param out the merged discovery output
     * @return what the merge holds
     */
    public static Summary of(List<Path> sources, Path out) {
        sources.stream().filter(source -> !discoveryOutput(source)).findFirst().ifPresent(source -> {
            throw new IllegalArgumentException(source + " is not a discovery output (no manifest.jsonl, repos.jsonl "
                    + "and blobs/)");
        });
        try {
            Files.createDirectories(out.resolve("blobs"));
            Map<String, List<String>> merged = FILES.stream().collect(Collectors.toMap(Function.identity(),
                    file -> lines(sources, file)));
            merged.forEach((file, lines) -> write(out.resolve(file), lines));
            int blobs = copyBlobs(sources, "blobs", out.resolve("blobs"));
            Files.createDirectories(out.resolve("yaml"));
            copyBlobs(sources, "yaml", out.resolve("yaml"));
            return new Summary(merged.get("manifest.jsonl").size(), merged.get("repos.jsonl").size(), blobs,
                    repeated(sources));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean discoveryOutput(Path source) {
        return Files.isRegularFile(source.resolve("manifest.jsonl"))
                && Files.isRegularFile(source.resolve("repos.jsonl")) && Files.isDirectory(source.resolve("blobs"));
    }

    static int copyBlobs(List<Path> sources, String folder, Path target) throws IOException {
        sources.stream()
                .map(source -> source.resolve(folder))
                .filter(Files::isDirectory)
                .flatMap(directory -> stored(directory).stream())
                .filter(blob -> !Files.exists(target.resolve(blob.getFileName())))
                .forEach(blob -> copy(blob, target.resolve(blob.getFileName())));
        try (Stream<Path> stored = Files.list(target)) {
            return (int) stored.count();
        }
    }

    // A .part is a download a killed run left unfinished.
    static List<Path> stored(Path directory) {
        try (Stream<Path> blobs = Files.list(directory)) {
            return blobs.filter(b -> Files.isRegularFile(b) && !b.toString().endsWith(".part")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copy(Path blob, Path target) {
        try {
            Files.copy(blob, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, List<String> lines) {
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> lines(List<Path> sources, String file) {
        Set<String> lines = new LinkedHashSet<>();
        sources.stream().map(source -> source.resolve(file)).filter(Files::isRegularFile).forEach(path -> {
            try (Stream<String> read = Files.lines(path)) {
                read.filter(line -> !line.isBlank()).forEach(lines::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return new ArrayList<>(lines);
    }

    static List<String> repeated(List<Path> sources) {
        Map<String, Long> seen = sources.stream().flatMap(source -> lines(List.of(source), "repos.jsonl").stream()
                        .map(line -> JSON.readTree(line).path("repository").asString()).distinct())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return seen.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).sorted().toList();
    }
}
