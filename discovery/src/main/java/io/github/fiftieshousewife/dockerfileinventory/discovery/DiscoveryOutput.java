package io.github.fiftieshousewife.dockerfileinventory.discovery;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Appended as work finishes, so a rerun skips what a killed run had finished.
final class DiscoveryOutput {

    record BlobFinding(String sha, String repository, String problem) {
    }

    private final Path dir;
    private final Path blobs;
    private final Path yaml;
    private final ObjectMapper json = new ObjectMapper();

    DiscoveryOutput(Path dir) {
        this.dir = dir;
        this.blobs = dir.resolve("blobs");
        this.yaml = dir.resolve("yaml");
        try {
            Files.createDirectories(blobs);
            Files.createDirectories(yaml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Path dir() {
        return dir;
    }

    synchronized void record(RepoScan.Result result) {
        append("manifest.jsonl", result.dockerfiles());
        append("yaml.jsonl", result.yaml());
        append("repos.jsonl", List.of(result.outcome()));
    }

    synchronized void recordBlobFinding(BlobFinding finding) {
        append("blob-findings.jsonl", List.of(finding));
    }

    synchronized void recordChanges(List<LastChange> changes) {
        append("changes.jsonl", changes);
    }

    List<LastChange> changes() {
        return read("changes.jsonl", LastChange.class);
    }

    Set<String> finishedRepositories() {
        return finished(read("repos.jsonl", RepoOutcome.class));
    }

    static Set<String> finished(List<RepoOutcome> outcomes) {
        Map<String, RepoOutcome> last = outcomes.stream()
                .collect(Collectors.toMap(RepoOutcome::repository, Function.identity(), (earlier, later) -> later));
        return last.values().stream().filter(o -> o.status().isFinal()).map(RepoOutcome::repository)
                .collect(Collectors.toSet());
    }

    List<Located> manifest() {
        return read("manifest.jsonl", Located.class);
    }

    List<Located> yaml() {
        return read("yaml.jsonl", Located.class);
    }

    Set<String> reportedBlobs() {
        return read("blob-findings.jsonl", BlobFinding.class).stream().map(BlobFinding::sha)
                .collect(Collectors.toSet());
    }

    boolean hasBlob(String sha) {
        return Files.exists(blobs.resolve(sha));
    }

    boolean hasYaml(String sha) {
        return Files.exists(yaml.resolve(sha));
    }

    void storeBlob(String sha, String text) {
        store(blobs, sha, text);
    }

    // Apart from the Dockerfiles: the stages after discovery read every file in blobs/ as one.
    void storeYaml(String sha, String text) {
        store(yaml, sha, text);
    }

    // Moved into place whole, so a killed run never leaves half a file a rerun would take as stored.
    private static void store(Path folder, String sha, String text) {
        try {
            Path temp = Files.createTempFile(folder, sha, ".part");
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, folder.resolve(sha), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void append(String file, List<?> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try {
            Files.write(dir.resolve(file), rows.stream().map(json::writeValueAsString).toList(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    <T> List<T> read(String file, Class<T> type) {
        Path path = dir.resolve(file);
        if (Files.notExists(path)) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.filter(l -> !l.isBlank()).map(l -> json.readValue(l, type)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
