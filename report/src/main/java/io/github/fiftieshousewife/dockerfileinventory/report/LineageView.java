package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Lineage's output as the dashboard shows it: images by name, parents by name, Dockerfiles by location. */
public record LineageView(Counts counts, List<ImageRow> images, List<BaseUse> bases, List<String> gaps,
                          List<OutdatedRow> outdated) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AMBIGUOUS = "ambiguous: ";

    public LineageView {
        images = List.copyOf(images);
        bases = List.copyOf(bases);
        gaps = List.copyOf(gaps);
        outdated = List.copyOf(outdated);
    }

    /** An image built on {@code base} when a newer image of that name, {@code newer}, exists. */
    public record OutdatedRow(String image, String base, String newer) {
    }

    /** An image others are built on, and how many images are built directly on it. */
    public record BaseUse(String name, int children) {
    }

    /** Lineage's own rates: images read, the share with a known parent, links by tier, and the three lists' sizes. */
    public record Counts(int images, double coverage, Map<String, Integer> linksByTier, int gaps, int externalBases,
                         int outdatedBases) {

        public Counts {
            linksByTier = Map.copyOf(linksByTier);
        }
    }

    /**
     * One image: {@code repository/image:tag} (its newest tag, when it has several), its parent's name (null for a
     * root), its link tier, and its Dockerfiles as {@code repository: path}.
     */
    public record ImageRow(String name, String parent, String tier, List<String> dockerfiles) {

        public ImageRow {
            dockerfiles = List.copyOf(dockerfiles);
        }
    }

    public static LineageView read(Path lineageDir, Map<String, List<Location>> locations) {
        Map<String, String> names = names(lineageDir);
        Map<String, String> parents = new HashMap<>();
        Estate.lines(lineageDir.resolve("lineage.jsonl")).forEach(row ->
                parents.put(row.path("configDigest").asString(), parent(row.path("parent"), names)));
        List<ImageRow> images = Estate.lines(lineageDir.resolve("links.jsonl")).stream()
                .filter(row -> names.containsKey(row.path("configDigest").asString()))
                .map(row -> new ImageRow(names.get(row.path("configDigest").asString()),
                        parents.get(row.path("configDigest").asString()), row.path("tier").asString(),
                        dockerfiles(row.path("blobShas"), locations)))
                .sorted(Comparator.comparing(ImageRow::name))
                .toList();
        return new LineageView(counts(lineageDir.resolve("rates.json")), images, bases(images),
                gaps(lineageDir.resolve("gaps.jsonl"), names),
                outdated(lineageDir.resolve("outdated-bases.jsonl"), names));
    }

    /** Images with children, most children first, then by name. */
    static List<BaseUse> bases(List<ImageRow> images) {
        return images.stream()
                .filter(i -> i.parent() != null && !i.parent().startsWith(AMBIGUOUS))
                .collect(Collectors.groupingBy(ImageRow::parent, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new BaseUse(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(BaseUse::children).reversed().thenComparing(BaseUse::name))
                .toList();
    }

    private static List<String> gaps(Path file, Map<String, String> names) {
        return Estate.lines(file).stream()
                .sorted(Comparator.comparing(LineageView::pushed).reversed())
                .map(row -> name(row.path("configDigest").asString(), names))
                .toList();
    }

    private static List<OutdatedRow> outdated(Path file, Map<String, String> names) {
        return Estate.lines(file).stream()
                .map(row -> new OutdatedRow(name(row.path("image").asString(), names),
                        name(row.path("parent").asString(), names), name(row.path("newer").asString(), names)))
                .sorted(Comparator.comparing(OutdatedRow::image))
                .toList();
    }

    private static String name(String digest, Map<String, String> names) {
        return names.getOrDefault(digest, digest);
    }

    private static Counts counts(Path ratesFile) {
        JsonNode rates = Files.exists(ratesFile) ? JSON.readTree(ratesFile.toFile()) : JSON.createObjectNode();
        Map<String, Integer> links = new TreeMap<>();
        rates.path("links").properties().forEach(e -> links.put(e.getKey(), e.getValue().asInt()));
        return new Counts(rates.path("images").asInt(), rates.path("lineageCoverage").asDouble(), links,
                rates.path("gaps").asInt(), rates.path("externalBases").asInt(), rates.path("outdatedBases").asInt());
    }

    /** Each image's name: its most recently pushed tag. */
    private static Map<String, String> names(Path lineageDir) {
        Map<String, JsonNode> newest = new HashMap<>();
        Estate.lines(lineageDir.resolve("images.jsonl")).stream()
                .filter(row -> !row.path("configDigest").isNull())
                .forEach(row -> newest.merge(row.path("configDigest").asString(), row,
                        (a, b) -> pushed(a).isAfter(pushed(b)) ? a : b));
        Map<String, String> names = new HashMap<>();
        newest.forEach((digest, row) -> names.put(digest, row.path("repository").asString() + "/"
                + row.path("image").asString() + ":" + row.path("tag").asString()));
        return names;
    }

    private static Instant pushed(JsonNode row) {
        return row.path("lastPushed").isNull() ? Instant.MIN : Instant.parse(row.path("lastPushed").asString());
    }

    private static String parent(JsonNode parent, Map<String, String> names) {
        return switch (parent.path("kind").asString()) {
            case "Of" -> name(parent.path("configDigest").asString(), names);
            case "Ambiguous" -> AMBIGUOUS + String.join(", ", StreamSupport
                    .stream(parent.path("configDigests").spliterator(), false)
                    .map(d -> name(d.asString(), names)).toList());
            default -> null;
        };
    }

    private static List<String> dockerfiles(JsonNode blobShas, Map<String, List<Location>> locations) {
        return StreamSupport.stream(blobShas.spliterator(), false)
                .flatMap(blob -> locations.getOrDefault(blob.asString(), List.of()).stream())
                .map(l -> l.repository() + ": " + l.path())
                .sorted()
                .toList();
    }
}
