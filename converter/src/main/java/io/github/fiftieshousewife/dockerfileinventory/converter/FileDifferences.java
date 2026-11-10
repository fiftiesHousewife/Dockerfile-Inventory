package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.stream.Stream;

final class FileDifferences {

    static final List<String> VOLATILE_DIRECTORIES = List.of("/var/lib/rpm/", "/var/lib/dnf/", "/var/lib/dpkg/",
            "/var/lib/apt/", "/var/log/", "/var/cache/", "/root/.cache/", "/tmp/");
    static final List<String> VOLATILE_FILES = List.of("/etc/ld.so.cache", "/etc/machine-id", "*.pyc");

    private FileDifferences() {
    }

    static List<String> of(SortedMap<String, FileEntry> original,
            SortedMap<String, FileEntry> regenerated) {
        TreeSet<String> paths = new TreeSet<>(original.keySet());
        paths.addAll(regenerated.keySet());
        return paths.stream().flatMap(path -> path(path, original.get(path), regenerated.get(path)).stream()).toList();
    }

    static List<String> path(String path, FileEntry before, FileEntry after) {
        return before == null || after == null
                ? List.of(path + ": only in the " + (before == null ? "regenerated" : "original"))
                : entry(path, before, after);
    }

    static List<String> entry(String path, FileEntry before, FileEntry after) {
        return Stream.of(
                        attribute(path, "type", before.type(), after.type()),
                        attribute(path, "mode", before.mode(), after.mode()),
                        attribute(path, "owner", before.uid() + ":" + before.gid(), after.uid() + ":" + after.gid()),
                        attribute(path, "link target", before.target(), after.target()),
                        content(path, before, after))
                .flatMap(Optional::stream)
                .toList();
    }

    static Optional<String> attribute(String path, String what, String before, String after) {
        return Objects.equals(before, after) ? Optional.empty()
                : Optional.of(path + ": " + what + " " + before + " became " + after);
    }

    static Optional<String> content(String path, FileEntry before, FileEntry after) {
        return volatileContent(path) || Objects.equals(before.sha256(), after.sha256()) ? Optional.empty()
                : Optional.of(path + ": content differs");
    }

    static boolean volatileContent(String path) {
        return VOLATILE_DIRECTORIES.stream().anyMatch(path::startsWith) || VOLATILE_FILES.stream()
                .anyMatch(file -> file.startsWith("*") ? path.endsWith(file.substring(1)) : path.equals(file));
    }
}
