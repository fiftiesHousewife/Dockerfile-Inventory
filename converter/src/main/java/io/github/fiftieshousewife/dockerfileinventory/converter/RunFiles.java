package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

final class RunFiles {

    private final Path discoveryDir;
    private final Path outDir;

    RunFiles(Path discoveryDir, Path outDir) {
        this.discoveryDir = discoveryDir;
        this.outDir = outDir;
    }

    Map<String, List<Location>> locations(ObjectMapper json) {
        Path manifest = discoveryDir.resolve("manifest.jsonl");
        return Files.exists(manifest) ? locations(read(manifest), json) : Map.of();
    }

    static Map<String, List<Location>> locations(String manifest, ObjectMapper json) {
        return manifest.lines().filter(line -> !line.isBlank()).map(json::readTree)
                .collect(Collectors.groupingBy(row -> row.path("blobSha").asString(), LinkedHashMap::new,
                        Collectors.mapping(row -> new Location(row.path("repository").asString(),
                                row.path("path").asString()), Collectors.toList())));
    }

    List<Path> blobs() {
        try (Stream<Path> files = Files.list(discoveryDir.resolve("blobs"))) {
            return files.filter(f -> Files.isRegularFile(f) && !String.valueOf(f.getFileName()).endsWith(".part"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void writeFile(Path file, String text) {
        try {
            createParent(file);
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void createParent(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    void write(String file, String text) {
        try {
            Path target = outDir.resolve(file);
            Path folder = outDir.resolve(file.contains("/") ? file.substring(0, file.lastIndexOf('/')) : "");
            Files.createDirectories(folder);
            Files.writeString(target, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
