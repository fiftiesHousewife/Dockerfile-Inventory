package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 1 over a discovery output directory: every blob in {@code blobs/} is parsed once and written to
 * {@code facts.jsonl} or {@code parse-failures.jsonl} under the output directory, and {@code rates.json} is
 * rewritten from every row recorded so far.
 */
public final class FactsRun {

    /** One row of {@code facts.jsonl}. */
    public record FactRow(String blobSha, String textHash, String signatureHash, String signature, ImageFacts facts) {
    }

    public record Summary(int parsed, int failed, int alreadyDone) {
    }

    /** One row of {@code parse-failures.jsonl}. */
    public record FailureRow(String blobSha, String message) {
    }

    private final Path discoveryDir;
    private final Path outDir;
    private final ObjectMapper json = JsonMapper.builder().build();
    private final FactsParser parser = new FactsParser();

    public FactsRun(Path discoveryDir, Path outDir) {
        this.discoveryDir = discoveryDir;
        this.outDir = outDir;
    }

    /** Parses every blob not already recorded; rows are appended one at a time, so an interrupted run resumes. */
    public Summary run() throws IOException {
        Files.createDirectories(outDir);
        Set<String> done = new HashSet<>();
        read("facts.jsonl", FactRow.class).forEach(r -> done.add(r.blobSha()));
        read("parse-failures.jsonl", FailureRow.class).forEach(r -> done.add(r.blobSha()));
        int parsed = 0;
        int failed = 0;
        int alreadyDone = 0;
        for (Path blob : blobs()) {
            String sha = String.valueOf(blob.getFileName());
            if (done.contains(sha)) {
                alreadyDone++;
                continue;
            }
            String text = Files.readString(blob, StandardCharsets.UTF_8);
            switch (parser.parse(sha, text)) {
                case ParseResult.Parsed result -> {
                    ImageFacts facts = FactExtractor.extract(result.file());
                    append("facts.jsonl", new FactRow(sha, NormalForms.textHash(text), NormalForms.signatureHash(facts),
                            NormalForms.signature(facts), facts));
                    parsed++;
                }
                case ParseResult.Failed result -> {
                    append("parse-failures.jsonl", new FailureRow(sha, result.message()));
                    failed++;
                }
            }
        }
        Rates rates = Rates.of(read("facts.jsonl", FactRow.class), read("parse-failures.jsonl", FailureRow.class), locations());
        Files.writeString(outDir.resolve("rates.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(rates),
                StandardCharsets.UTF_8);
        return new Summary(parsed, failed, alreadyDone);
    }

    /** (repository, blob SHA) for every Dockerfile location in discovery's manifest. */
    private List<Map.Entry<String, String>> locations() throws IOException {
        Path manifest = discoveryDir.resolve("manifest.jsonl");
        if (Files.notExists(manifest)) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(manifest, StandardCharsets.UTF_8)) {
            return lines.filter(l -> !l.isBlank())
                    .map(json::readTree)
                    .<Map.Entry<String, String>>map(n -> Map.entry(n.path("repository").asString(), n.path("blobSha").asString()))
                    .toList();
        }
    }

    <T> List<T> read(String file, Class<T> type) throws IOException {
        Path path = outDir.resolve(file);
        if (Files.notExists(path)) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.filter(l -> !l.isBlank()).map(l -> json.readValue(l, type)).toList();
        }
    }

    private List<Path> blobs() throws IOException {
        try (Stream<Path> files = Files.list(discoveryDir.resolve("blobs"))) {
            // Discovery writes a blob to <sha>...part, then renames it; a .part file is unfinished.
            return files.filter(f -> Files.isRegularFile(f) && !String.valueOf(f.getFileName()).endsWith(".part")).sorted().toList();
        }
    }

    private void append(String file, Object row) {
        try (BufferedWriter out = Files.newBufferedWriter(outDir.resolve(file), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            out.write(json.writeValueAsString(row));
            out.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
