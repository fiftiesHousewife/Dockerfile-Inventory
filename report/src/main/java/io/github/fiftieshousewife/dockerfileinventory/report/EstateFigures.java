package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

record EstateFigures(int dockerfiles, int unparsed, int locations, int images, int organisations,
                     Map<Integer, Integer> depths, Map<String, Integer> layerBands, int overLimit, int root,
                     int lowPort, int toolchain, Map<String, Integer> footprints, Map<String, Integer> runtimes,
                     Map<String, Integer> unusual, Map<String, Integer> unmaintained, int blockedImages) {

    static final List<String> BANDS = List.of("1-5", "6-10", "11-20", "21-40", "over 40");
    private static final Pattern TOOLCHAIN = Pattern.compile(
            "gcc|gcc-c\\+\\+|g\\+\\+|make|cmake|git|binutils|build-essential|golang|maven|gradle|.*-devel|.*-dev");

    static EstateFigures of(List<CatalogueEntry> entries) {
        List<CatalogueEntry> parsed = entries.stream().filter(e -> e.parseError() == null).toList();
        List<Hierarchies.BuiltImage> images = parsed.stream().flatMap(e -> e.hierarchy().images().stream()).toList();
        Map<String, Map<String, List<CatalogueEntry>>> rows = Breakdowns.of(parsed);
        return new EstateFigures(parsed.size(), entries.size() - parsed.size(),
                entries.stream().mapToInt(e -> e.locations().size()).sum(),
                (int) images.stream().map(Hierarchies.BuiltImage::configDigest).distinct().count(),
                (int) entries.stream().map(CatalogueView::organisation).distinct().count(),
                tally(parsed.stream().map(e -> e.links().depth()).toList()),
                bands(parsed.stream().map(e -> e.hierarchy().layers().total()).toList()),
                (int) parsed.stream().filter(e -> e.hierarchy().layers().overLimit()).count(),
                size(rows, "final-image", Breakdowns.FINAL_IMAGE.get(0)),
                size(rows, "final-image", Breakdowns.FINAL_IMAGE.get(1)),
                size(rows, "final-image", Breakdowns.FINAL_IMAGE.get(2)),
                Breakdowns.counts(rows.get("footprint")), Breakdowns.counts(rows.get("runtime")),
                Breakdowns.counts(rows.get("unusual")),
                largestFirst(unmaintained(parsed, images)),
                (int) images.stream().filter(i -> !i.blockedBy().isEmpty())
                        .map(Hierarchies.BuiltImage::configDigest).distinct().count());
    }

    private static int size(Map<String, Map<String, List<CatalogueEntry>>> rows, String section, String label) {
        return rows.get(section).getOrDefault(label, List.of()).size();
    }

    static Map<String, Integer> unmaintained(List<CatalogueEntry> parsed, List<Hierarchies.BuiltImage> images) {
        Map<String, Set<String>> byReason = new HashMap<>();
        images.forEach(image -> image.chain().forEach(step -> step.unmaintained().forEach(reason -> byReason
                .computeIfAbsent(reasonKind(reason), kind -> new HashSet<>()).add(step.configDigest()))));
        Map<String, Integer> counts = new TreeMap<>();
        byReason.forEach((kind, steps) -> counts.put(kind, steps.size()));
        int archived = (int) parsed.stream().filter(e -> !e.hierarchy().unmaintained().isEmpty()).count();
        if (archived > 0) {
            counts.put("in archived repositories only", archived);
        }
        return counts;
    }

    static Map<String, Integer> largestFirst(Map<String, Integer> counts) {
        return counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    static String reasonKind(String reason) {
        return reason.startsWith("not rebuilt") ? "not rebuilt on its updated base" : "not published for a while";
    }

    static boolean lowPort(List<String> ports) {
        return ports.stream().map(port -> port.replaceFirst("/.*$", "").replaceFirst("-.*$", ""))
                .filter(port -> port.matches("\\d{1,5}")).anyMatch(port -> Integer.parseInt(port) < 1024);
    }

    static boolean toolchain(CatalogueEntry.Build build) {
        Set<Integer> finals = Set.copyOf(Runtimes.finalStages(build.stages()));
        return build.packages().stream().anyMatch(p -> finals.contains(p.stage())
                && TOOLCHAIN.matcher(p.name()).matches());
    }

    static Map<String, Integer> bands(List<Integer> layers) {
        Map<String, Integer> tally = tally(layers.stream().map(EstateFigures::layerBand).toList());
        Map<String, Integer> ordered = new LinkedHashMap<>();
        BANDS.stream().filter(tally::containsKey).forEach(band -> ordered.put(band, tally.get(band)));
        return ordered;
    }

    static String layerBand(int layers) {
        if (layers <= 5) {
            return "1-5";
        }
        if (layers <= 10) {
            return "6-10";
        }
        if (layers <= 20) {
            return "11-20";
        }
        return layers <= 40 ? "21-40" : "over 40";
    }

    private static <K extends Comparable<K>> Map<K, Integer> tally(List<K> values) {
        return values.stream().collect(Collectors.toMap(v -> v, v -> 1, Integer::sum, TreeMap::new));
    }
}
