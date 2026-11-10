package io.github.fiftieshousewife.dockerfileinventory.facts;

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

final class ManifestRun {

    record Location(String repository, String path) {
    }

    record WorkloadRow(String blobSha, String kind, String name, String namespace, Workload.Pod pod,
                       List<Location> locations) {

        WorkloadRow {
            locations = List.copyOf(locations);
        }
    }

    record YamlRates(int files, int withWorkloads, int workloads, int templates, int notYaml) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path discoveryDir;
    private final Path outDir;

    ManifestRun(Path discoveryDir, Path outDir) {
        this.discoveryDir = discoveryDir;
        this.outDir = outDir;
    }

    record FileRead(String blobSha, List<Location> locations, Workloads.Read read) {

        FileRead {
            locations = List.copyOf(locations);
        }
    }

    YamlRates run() {
        List<FileRead> reads = locationsByBlob().entrySet().stream()
                .map(file -> new FileRead(file.getKey(), file.getValue(), Workloads.read(text(file.getKey()))))
                .toList();
        List<String> rows = reads.stream().flatMap(ManifestRun::rows).map(JSON::writeValueAsString).toList();
        YamlRates rates = rates(reads);
        write("workloads.jsonl", rows.isEmpty() ? "" : String.join("\n", rows) + "\n");
        write("yaml-rates.json", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(rates));
        return rates;
    }

    static Stream<WorkloadRow> rows(FileRead file) {
        return file.read().workloads().stream().map(w -> new WorkloadRow(file.blobSha(), w.kind(), w.name(),
                w.namespace(), w.pod(), file.locations()));
    }

    static YamlRates rates(List<FileRead> reads) {
        return new YamlRates(reads.size(), (int) reads.stream().filter(f -> !f.read().workloads().isEmpty()).count(),
                reads.stream().mapToInt(f -> f.read().workloads().size()).sum(), unread(reads, "a template"),
                unread(reads, "not YAML"));
    }

    static int unread(List<FileRead> reads, String why) {
        return (int) reads.stream().map(f -> f.read().unread()).filter(u -> u != null && u.startsWith(why)).count();
    }

    Map<String, List<Location>> locationsByBlob() {
        Path listed = discoveryDir.resolve("yaml.jsonl");
        if (!Files.isRegularFile(listed)) {
            return Map.of();
        }
        try (Stream<String> lines = Files.lines(listed, StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isBlank()).map(JSON::readTree)
                    .filter(row -> Files.isRegularFile(discoveryDir.resolve("yaml").resolve(row.path("blobSha")
                            .asString())))
                    .collect(Collectors.groupingBy(row -> row.path("blobSha").asString(), LinkedHashMap::new,
                            Collectors.mapping(row -> new Location(row.path("repository").asString(),
                                    row.path("path").asString()), Collectors.toList())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String text(String sha) {
        try {
            return Files.readString(discoveryDir.resolve("yaml").resolve(sha), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(String file, String text) {
        try {
            Files.createDirectories(outDir);
            Files.writeString(outDir.resolve(file), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
