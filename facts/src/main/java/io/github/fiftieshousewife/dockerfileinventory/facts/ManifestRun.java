package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// The YAML discovery fetched, read for workloads: workloads.jsonl (one line per workload, with its file's
// locations) and yaml-rates.json (the files read, those holding workloads, and those left unread and why). Written
// whole each run: reading YAML is cheap.
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

    YamlRates run() {
        Map<String, List<Location>> files = locations();
        List<String> rows = new ArrayList<>();
        int withWorkloads = 0;
        int templates = 0;
        int notYaml = 0;
        for (Map.Entry<String, List<Location>> file : files.entrySet()) {
            Workloads.Read read = Workloads.read(text(file.getKey()));
            if (read.unread() != null) {
                templates += read.unread().startsWith("a template") ? 1 : 0;
                notYaml += read.unread().startsWith("not YAML") ? 1 : 0;
            }
            withWorkloads += read.workloads().isEmpty() ? 0 : 1;
            read.workloads().forEach(w -> rows.add(JSON.writeValueAsString(new WorkloadRow(file.getKey(), w.kind(),
                    w.name(), w.namespace(), w.pod(), file.getValue()))));
        }
        YamlRates rates = new YamlRates(files.size(), withWorkloads, rows.size(), templates, notYaml);
        write("workloads.jsonl", rows.isEmpty() ? "" : String.join("\n", rows) + "\n");
        write("yaml-rates.json", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(rates));
        return rates;
    }

    // Each stored YAML file once, with every location that holds it.
    Map<String, List<Location>> locations() {
        Map<String, List<Location>> files = new LinkedHashMap<>();
        Path listed = discoveryDir.resolve("yaml.jsonl");
        if (!Files.isRegularFile(listed)) {
            return files;
        }
        try {
            for (String line : Files.readAllLines(listed, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode row = JSON.readTree(line);
                String sha = row.path("blobSha").asString();
                if (Files.isRegularFile(discoveryDir.resolve("yaml").resolve(sha))) {
                    files.computeIfAbsent(sha, s -> new ArrayList<>()).add(new Location(
                            row.path("repository").asString(), row.path("path").asString()));
                }
            }
            return files;
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
