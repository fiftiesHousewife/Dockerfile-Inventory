package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.FactExtractor;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

final class DiscoveryFiles {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path dir;

    DiscoveryFiles(Path dir) {
        this.dir = dir;
    }

    List<Subject> subjects() {
        FactsParser parser = new FactsParser();
        return blobs().stream().map(blob -> subject(parser, String.valueOf(blob.getFileName()), read(blob))).toList();
    }

    static Subject subject(FactsParser parser, String sha, String text) {
        return switch (parser.parse(sha, text)) {
            case ParseResult.Parsed parsed -> Subject.parsed(sha, FactExtractor.extract(parsed.file()), parsed.file());
            case ParseResult.Failed failed -> Subject.failed(sha, failed.message());
        };
    }

    List<Path> blobs() {
        try (Stream<Path> files = Files.list(dir.resolve("blobs"))) {
            return files.filter(f -> Files.isRegularFile(f) && !String.valueOf(f.getFileName()).endsWith(".part"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String text(String sha) {
        Path blob = dir.resolve("blobs").resolve(sha);
        return Files.exists(blob) ? read(blob) : null;
    }

    Map<String, List<Location>> locations() {
        Map<String, List<Location>> locations = new LinkedHashMap<>();
        Path manifest = dir.resolve("manifest.jsonl");
        if (Files.exists(manifest)) {
            read(manifest).lines().filter(l -> !l.isBlank()).map(JSON::readTree).forEach(row -> locations
                    .computeIfAbsent(row.path("blobSha").asString(), k -> new ArrayList<>())
                    .add(new Location(row.path("repository").asString(), row.path("path").asString())));
        }
        return locations;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
