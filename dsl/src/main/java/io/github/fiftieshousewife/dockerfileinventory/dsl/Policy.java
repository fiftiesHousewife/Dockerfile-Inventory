package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The approved bases (Phase 4, D1): the same {@code imagePath,tagPrefix} list Phase 3's S1 reads. A base is
 * approved when its image path (registry host removed, by Docker's rule) matches and its tag starts with the
 * prefix on a version boundary ({@code 9} covers {@code 9.4}, not {@code 91}); an empty prefix covers every tag.
 */
public record Policy(List<Approved> approved) {

    public record Approved(String imagePath, String tagPrefix) {
    }

    public Policy {
        approved = List.copyOf(approved);
    }

    public static Policy of(List<Approved> approved) {
        return new Policy(approved);
    }

    public static Policy read(Path csv) {
        try {
            return of(Files.readAllLines(csv).stream()
                    .filter(line -> !line.isBlank() && !line.startsWith("imagePath,"))
                    .map(line -> line.split(",", -1))
                    .map(f -> new Approved(f[0].strip(), f.length > 1 ? f[1].strip() : ""))
                    .toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean approves(BaseImage base) {
        String path = path(base.name());
        String tag = base.tag() == null ? "" : base.tag();
        return approved.stream().anyMatch(a -> a.imagePath().equals(path) && covers(a.tagPrefix(), tag));
    }

    private static boolean covers(String prefix, String tag) {
        return prefix.isEmpty() || tag.equals(prefix) || tag.startsWith(prefix + ".") || tag.startsWith(prefix + "-");
    }

    private static String path(String name) {
        int slash = name.indexOf('/');
        String first = slash < 0 ? "" : name.substring(0, slash);
        boolean host = first.contains(".") || first.contains(":") || first.equals("localhost");
        return host ? name.substring(slash + 1) : name;
    }
}
