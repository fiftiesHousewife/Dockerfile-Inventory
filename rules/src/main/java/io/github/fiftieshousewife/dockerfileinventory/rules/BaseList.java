package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class BaseList {

    record Entry(String path, String tagPrefix, String status, String eolDate) {
    }

    private final List<Entry> entries;

    BaseList(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    static BaseList read(Path csv) {
        try {
            return new BaseList(Files.readAllLines(csv).stream()
                    .filter(line -> !line.isBlank() && !line.startsWith("imagePath,"))
                    .map(BaseList::entry)
                    .toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Entry entry(String line) {
        String[] f = line.split(",", -1);
        return new Entry(f[0].strip(), f.length > 1 ? f[1].strip() : "",
                f.length > 2 ? f[2].strip() : null, f.length > 3 ? f[3].strip() : null);
    }

    Optional<Entry> lookup(String path, String tag) {
        String version = tag == null ? "" : tag;
        return entries.stream()
                .filter(e -> e.path().equals(path) && covers(e.tagPrefix(), version))
                .max(Comparator.comparingInt(e -> e.tagPrefix().length()));
    }

    static boolean covers(String prefix, String tag) {
        return prefix.isEmpty() || tag.equals(prefix) || tag.startsWith(prefix + ".") || tag.startsWith(prefix + "-");
    }
}
