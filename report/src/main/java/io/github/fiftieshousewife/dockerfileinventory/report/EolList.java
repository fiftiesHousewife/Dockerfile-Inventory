package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The internal end-of-life list (Q4): a CSV of {@code imagePath,tagPrefix,status,eolDate}, header optional. A base
 * takes the entry for its image path with the longest tag prefix that ends on a version boundary, so {@code 9}
 * covers {@code 9.4} and {@code 9-1100} but not {@code 91}; an empty prefix covers every tag.
 */
public final class EolList {

    record Entry(String path, String tagPrefix, String status, LocalDate eolDate) {
    }

    private final List<Entry> entries;

    private EolList(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static EolList empty() {
        return new EolList(List.of());
    }

    public static EolList read(Path csv) {
        try {
            return new EolList(Files.readAllLines(csv).stream()
                    .filter(line -> !line.isBlank() && !line.startsWith("imagePath,"))
                    .map(line -> line.split(",", -1))
                    .map(f -> new Entry(f[0].strip(), f[1].strip(), f[2].strip(),
                            f.length > 3 && !f[3].isBlank() ? LocalDate.parse(f[3].strip()) : null))
                    .toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Optional<Entry> lookup(String path, String tag) {
        String version = tag == null ? "" : tag;
        return entries.stream()
                .filter(e -> e.path().equals(path) && covers(e.tagPrefix(), version))
                .max(Comparator.comparingInt(e -> e.tagPrefix().length()));
    }

    private static boolean covers(String prefix, String tag) {
        return prefix.isEmpty() || tag.equals(prefix) || tag.startsWith(prefix + ".") || tag.startsWith(prefix + "-");
    }
}
