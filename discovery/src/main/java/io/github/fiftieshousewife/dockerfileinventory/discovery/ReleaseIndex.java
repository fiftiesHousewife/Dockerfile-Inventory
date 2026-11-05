package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Last-released commit per repository, exported from the release database as CSV:
 * {@code owner/repo,commitSha} per line, an optional header, {@code #} comments. Repository names
 * compare case-insensitively, as GitHub's do.
 */
public final class ReleaseIndex {

    private final Map<String, String> commits;

    private ReleaseIndex(Map<String, String> commits) {
        this.commits = Map.copyOf(commits);
    }

    public static ReleaseIndex empty() {
        return new ReleaseIndex(Map.of());
    }

    public static ReleaseIndex read(Path csv) throws IOException {
        Map<String, String> commits = new HashMap<>();
        var lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#") || (i == 0 && line.toLowerCase(Locale.ROOT).startsWith("repo"))) {
                continue;
            }
            String[] cells = line.split(",", -1);
            if (cells.length < 2 || cells[0].isBlank() || !isSha(cells[1].strip())) {
                throw new IllegalArgumentException("%s line %d: expected owner/repo,commitSha but was '%s'"
                        .formatted(csv, i + 1, line));
            }
            commits.put(key(cells[0]), cells[1].strip());
        }
        return new ReleaseIndex(commits);
    }

    public Optional<String> releasedCommit(String repository) {
        return Optional.ofNullable(commits.get(key(repository)));
    }

    public int size() {
        return commits.size();
    }

    static boolean isSha(String value) {
        return value.matches("[0-9a-fA-F]{40}");
    }

    private static String key(String repository) {
        return repository.strip().toLowerCase(Locale.ROOT);
    }
}
