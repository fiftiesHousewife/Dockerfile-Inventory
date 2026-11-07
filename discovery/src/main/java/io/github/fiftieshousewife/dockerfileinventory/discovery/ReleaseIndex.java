package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class ReleaseIndex {

    private final Map<String, String> commits;

    private ReleaseIndex(Map<String, String> commits) {
        this.commits = Map.copyOf(commits);
    }

    static ReleaseIndex empty() {
        return new ReleaseIndex(Map.of());
    }

    static ReleaseIndex read(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        return new ReleaseIndex(IntStream.range(0, lines.size())
                .filter(i -> !skipped(lines.get(i).strip(), i))
                .mapToObj(i -> row(csv, i, lines.get(i).strip()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, last) -> last)));
    }

    static boolean skipped(String line, int index) {
        return line.isEmpty() || line.startsWith("#")
                || (index == 0 && line.toLowerCase(Locale.ROOT).startsWith("repo"));
    }

    static Map.Entry<String, String> row(Path csv, int index, String line) {
        String[] cells = line.split(",", -1);
        if (cells.length < 2 || cells[0].isBlank() || !isSha(cells[1].strip())) {
            throw new IllegalArgumentException("%s line %d: expected owner/repo,commitSha but was '%s'"
                    .formatted(csv, index + 1, line));
        }
        return Map.entry(key(cells[0]), cells[1].strip());
    }

    Optional<String> releasedCommit(String repository) {
        return Optional.ofNullable(commits.get(key(repository)));
    }

    int size() {
        return commits.size();
    }

    static boolean isSha(String value) {
        return value.matches("[0-9a-fA-F]{40}");
    }

    // GitHub's repository names compare case-insensitively.
    private static String key(String repository) {
        return repository.strip().toLowerCase(Locale.ROOT);
    }
}
