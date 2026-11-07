package io.github.fiftieshousewife.dockerfileinventory.discovery;

import tools.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
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

// Rows are appended as each repository finishes, so a killed run loses at most the repositories in flight and a
// rerun reads what is there to skip finished work.
final class DiscoveryOutput {

    record BlobFinding(String sha, String repository, String problem) {
    }

    private final Path dir;
    private final Path blobs;
    private final ObjectMapper json = new ObjectMapper();

    DiscoveryOutput(Path dir) {
        this.dir = dir;
        this.blobs = dir.resolve("blobs");
        try {
            Files.createDirectories(blobs);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    synchronized void record(RepoScan.Result result) {
        append("manifest.jsonl", result.found());
        append("repos.jsonl", List.of(result.outcome()));
    }

    synchronized void recordBlobFinding(BlobFinding finding) {
        append("blob-findings.jsonl", List.of(finding));
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

    Set<String> reportedBlobs() {
        return read("blob-findings.jsonl", BlobFinding.class).stream().map(BlobFinding::sha)
                .collect(Collectors.toSet());
    }

    boolean hasBlob(String sha) {
        return Files.exists(blobs.resolve(sha));
    }

    // Through a temporary file, so a killed run never leaves half a blob that a rerun would take as stored.
    void storeBlob(String sha, String text) {
        try {
            Path temp = Files.createTempFile(blobs, sha, ".part");
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, blobs.resolve(sha), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void append(String file, List<?> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try (BufferedWriter out = Files.newBufferedWriter(dir.resolve(file), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (Object row : rows) {
                out.write(json.writeValueAsString(row));
                out.newLine();
            }
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
