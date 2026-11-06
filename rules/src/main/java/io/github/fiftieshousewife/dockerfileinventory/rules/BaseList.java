package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A list of bases by image path and tag prefix: the approved list (S1, {@code imagePath,tagPrefix}) or the
 * end-of-life list (R2, {@code imagePath,tagPrefix,status,eolDate}); a header line is optional. A base takes the
 * entry for its path with the longest tag prefix ending on a version boundary: {@code 9} covers {@code 9.4} and
 * {@code 9-1100}, not {@code 91}; an empty prefix covers every tag. Phase 2's catalogue reads the same way.
 */
final class BaseList {

    record Entry(String path, String tagPrefix, String status, String eolDate) {
    }

    private final List<Entry> entries;

    private BaseList(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    static BaseList read(Path csv) {
        try {
            return new BaseList(Files.readAllLines(csv).stream()
                    .filter(line -> !line.isBlank() && !line.startsWith("imagePath,"))
                    .map(line -> line.split(",", -1))
                    .map(f -> new Entry(f[0].strip(), f.length > 1 ? f[1].strip() : "",
                            f.length > 2 ? f[2].strip() : null, f.length > 3 ? f[3].strip() : null))
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
