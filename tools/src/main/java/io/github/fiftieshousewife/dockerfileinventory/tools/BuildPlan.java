package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

// The lineage demo's builds: sclorg's CentOS Stream 9 Dockerfiles discovery found, each named as sclorg publishes its
// image and built FROM the image built before it; the chain, or every one.
final class BuildPlan {

    private static final Pattern CHAIN = Pattern.compile("sclorg/s2i-base-container\t[0-9a-f]+\t(core|base)/"
            + "|sclorg/s2i-python-container\t[0-9a-f]+\t3\\.12/|sclorg/nginx-container\t[0-9a-f]+\t1\\.24/");

    record Build(String name, String repository, String commit, String path, String base) {

        String line() {
            return String.join("\t", name, repository, commit, path, base);
        }
    }

    private BuildPlan() {
    }

    static List<Build> all(Path discovery) {
        TreeSet<String> rows = new TreeSet<>();
        Json.rows(discovery.resolve("manifest.jsonl")).filter(row -> row.path("path").asString().endsWith(
                "Dockerfile.c9s")).forEach(row -> rows.add(String.join("\t", row.path("repository").asString(),
                row.path("commitSha").asString(), row.path("path").asString(), row.path("blobSha").asString())));
        return rows.stream().map(row -> row.split("\t")).map(fields -> new Build(name(fields[0], fields[2]),
                fields[0], fields[1], fields[2], base(discovery.resolve("blobs").resolve(fields[3])))).toList();
    }

    static String name(String repository, String path) {
        if (repository.equals("sclorg/s2i-base-container") && path.startsWith("core/")) {
            return "sclorg/s2i-core-c9s:c9s";
        }
        if (repository.equals("sclorg/s2i-base-container") && path.startsWith("base/")) {
            return "sclorg/s2i-base-c9s:c9s";
        }
        int slash = path.lastIndexOf('/');
        String part = repository.replaceFirst("^sclorg/", "") + (slash < 0 ? "" : "-" + path.substring(0, slash));
        return "sclorg/" + part.toLowerCase(Locale.ROOT).replace('/', '-').replace('.', '-') + "-c9s:c9s";
    }

    // The first FROM's image, as written.
    static String base(Path blob) {
        try {
            return Files.readAllLines(blob).stream().filter(line -> line.toUpperCase(Locale.ROOT).startsWith("FROM"))
                    .map(line -> line.trim().split("\\s+")).filter(words -> words.length > 1).map(words -> words[1])
                    .findFirst().orElse("");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Build> chain(List<Build> all) {
        return all.stream().filter(build -> CHAIN.matcher(build.line()).find()).toList();
    }

    // Parents before children: the CentOS roots' children, then s2i-core's, then s2i-base's.
    static List<Build> ordered(List<Build> plan) {
        return plan.stream().sorted(Comparator.comparingInt(BuildPlan::rank)).toList();
    }

    private static int rank(Build build) {
        return build.base().contains("s2i-base") ? 2 : build.base().contains("s2i-core") ? 1 : 0;
    }
}
