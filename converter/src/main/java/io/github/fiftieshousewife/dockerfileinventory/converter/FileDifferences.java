package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeSet;

final class FileDifferences {

    // Files that hold the time they were built in their content: two builds minutes apart differ there anyway.
    static final List<String> VOLATILE_DIRECTORIES = List.of("/var/lib/rpm/", "/var/lib/dnf/", "/var/lib/dpkg/",
            "/var/lib/apt/", "/var/log/", "/var/cache/", "/root/.cache/", "/tmp/");
    static final List<String> VOLATILE_FILES = List.of("/etc/ld.so.cache", "/etc/machine-id", "*.pyc");

    private FileDifferences() {
    }

    static List<String> of(SortedMap<String, FileEntry> original,
            SortedMap<String, FileEntry> regenerated) {
        TreeSet<String> paths = new TreeSet<>(original.keySet());
        paths.addAll(regenerated.keySet());
        return paths.stream().flatMap(path -> {
            FileEntry before = original.get(path);
            FileEntry after = regenerated.get(path);
            if (before == null || after == null) {
                return List.of(path + ": only in the " + (before == null ? "regenerated" : "original")).stream();
            }
            return entry(path, before, after).stream();
        }).toList();
    }

    static List<String> entry(String path, FileEntry before, FileEntry after) {
        List<String> differences = new ArrayList<>();
        attribute(differences, path, "type", before.type(), after.type());
        attribute(differences, path, "mode", before.mode(), after.mode());
        attribute(differences, path, "owner", before.uid() + ":" + before.gid(), after.uid() + ":" + after.gid());
        attribute(differences, path, "link target", before.target(), after.target());
        if (!volatileContent(path) && !Objects.equals(before.sha256(), after.sha256())) {
            differences.add(path + ": content differs");
        }
        return differences;
    }

    static boolean volatileContent(String path) {
        return VOLATILE_DIRECTORIES.stream().anyMatch(path::startsWith) || VOLATILE_FILES.stream()
                .anyMatch(file -> file.startsWith("*") ? path.endsWith(file.substring(1)) : path.equals(file));
    }

    private static void attribute(List<String> differences, String path, String what, String before, String after) {
        if (!Objects.equals(before, after)) {
            differences.add(path + ": " + what + " " + before + " became " + after);
        }
    }
}
