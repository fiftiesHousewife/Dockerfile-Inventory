package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class EolList {

    record Entry(String path, String tagPrefix, String status, LocalDate eolDate) {
    }

    private final List<Entry> entries;

    private EolList(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    static EolList empty() {
        return new EolList(List.of());
    }

    static EolList read(Path csv) {
        try {
            return new EolList(Files.readAllLines(csv).stream()
                    .filter(line -> !line.isBlank() && !line.startsWith("imagePath,"))
                    .map(EolList::entry)
                    .toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Entry entry(String line) {
        String[] f = line.split(",", -1);
        return new Entry(f[0].strip(), f[1].strip(), f[2].strip(),
                f.length > 3 && !f[3].isBlank() ? LocalDate.parse(f[3].strip()) : null);
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
