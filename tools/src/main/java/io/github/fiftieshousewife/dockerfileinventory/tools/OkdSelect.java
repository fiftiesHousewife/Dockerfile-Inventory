package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * Picks the OKD images for a lineage run from one release on quay.io (read anonymously) and writes what the next
 * steps read: {@code images.tsv} (each component's tag, source repository and commit), {@code mirror.txt} (each
 * image to copy and its name in JCR) and {@code releases.csv} (discovery's {@code RELEASE_INDEX}).
 * {@code selectOkd --args="[release] [output directory]"}; settings {@code DISCOVERY_DIR}
 * ({@code out/openshift/discovery}) and {@code COUNT} (30).
 */
public final class OkdSelect {

    static final String DEFAULT_RELEASE = "5.0.0-okd-scos.5";

    private final Quay quay;
    private final String release;
    private final Path out;
    private final Path discovery;
    private final int count;

    OkdSelect(Quay quay, String release, Path out, Path discovery, int count) {
        this.quay = quay;
        this.release = release;
        this.out = out;
        this.discovery = discovery;
        this.count = count;
    }

    /**
     * Picks from the release and into the directory the arguments name, or the defaults.
     *
     * @param args {@code [release] [output directory]}
     */
    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        String release = args.length > 0 ? args[0] : DEFAULT_RELEASE;
        new OkdSelect(new QuayHttp(env), release, Path.of(args.length > 1 ? args[1] : "out/okd"),
                Path.of(env.getOrDefault("DISCOVERY_DIR", "out/openshift/discovery")),
                Integer.parseInt(env.getOrDefault("COUNT", "30"))).run();
    }

    void run() {
        if (!Files.isRegularFile(discovery.resolve("repos.jsonl"))) {
            throw new IllegalArgumentException("no " + discovery.resolve("repos.jsonl") + "; run discovery on "
                    + "openshift first");
        }
        List<String> tags = quay.tags(release).stream().distinct().sorted().toList();
        write("tags.txt", tags);
        Map<String, JsonNode> configs = new HashMap<>();
        tags.forEach(tag -> configs.put(tag, Json.MAPPER.readTree(config(tag))));
        List<OkdChoice.Image> images = tags.stream().map(tag -> image(tag, configs.get(tag))).toList();
        write("images.tsv", images.stream().map(OkdSelect::row).toList());
        Map<String, List<String>> bases = new HashMap<>();
        configs.forEach((tag, config) -> bases.put(tag, bases(config)));
        OkdChoice choice = OkdChoice.of(OkdChoice.candidates(release, images, scanned(discovery), count), bases,
                count);
        write("candidates.tsv", choice.candidates().stream().map(OkdSelect::row).toList());
        write("chosen.tsv", choice.chosen().stream().map(OkdSelect::row).toList());
        write("bases.txt", choice.bases());
        write("mirror.txt", choice.mirror(release));
        write("releases.csv", choice.releases());
        System.out.println("chosen: " + choice.chosen().size() + " components and " + choice.bases().size()
                + " bases; next: discovery with RELEASE_INDEX=" + out.resolve("releases.csv"));
    }

    // Fetched once: a config read before is kept in configs/.
    private String config(String tag) {
        Path cached = out.resolve("configs").resolve(tag + ".json");
        try {
            if (Files.isRegularFile(cached) && Files.size(cached) > 0) {
                return Files.readString(cached);
            }
            String config = quay.config(tag);
            Files.createDirectories(out.resolve("configs"));
            Files.writeString(cached, config);
            return config;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static OkdChoice.Image image(String tag, JsonNode config) {
        JsonNode labels = config.path("config").path("Labels");
        return new OkdChoice.Image(tag, labels.path("io.openshift.build.source-location").asString("")
                .replaceFirst("^https?://[^/]+/", ""), labels.path("io.openshift.build.commit.id").asString(""));
    }

    static List<String> bases(JsonNode config) {
        List<String> bases = new ArrayList<>();
        config.path("history").forEach(step -> {
            String comment = step.path("comment").asString("");
            if (comment.startsWith("FROM ") && !comment.equals("FROM oci-archive")) {
                bases.add(comment.substring(5));
            }
        });
        return bases;
    }

    static Set<String> scanned(Path discovery) {
        return Json.rows(discovery.resolve("repos.jsonl")).filter(row -> row.path("dockerfiles").asInt(0) > 0)
                .map(row -> row.path("repository").asString()).collect(Collectors.toSet());
    }

    private static String row(OkdChoice.Image image) {
        return image.tag() + "\t" + image.repository() + "\t" + image.commit();
    }

    private void write(String file, List<String> lines) {
        try {
            Files.createDirectories(out);
            Files.write(out.resolve(file), lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
