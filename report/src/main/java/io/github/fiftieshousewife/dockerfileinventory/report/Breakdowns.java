package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// The Dockerfiles behind each row of the summary, by section and the row's label: what its counts are the sizes of,
// and what its lists show.
final class Breakdowns {

    static final List<String> FINAL_IMAGE = List.of("Runs as root", "Exposes a port below 1024", "Ships a toolchain");

    private Breakdowns() {
    }

    static Map<String, Map<String, List<CatalogueEntry>>> of(List<CatalogueEntry> entries) {
        List<CatalogueEntry> parsed = entries.stream().filter(e -> e.parseError() == null).toList();
        Map<String, Map<String, List<CatalogueEntry>>> groups = new LinkedHashMap<>();
        groups.put("depth", ordered(grouped(parsed, e -> Stream.of(String.valueOf(e.links().depth()))),
                parsed.stream().map(e -> e.links().depth()).distinct().sorted().map(String::valueOf).toList()));
        groups.put("layers", ordered(grouped(parsed, e -> Stream.of(EstateFigures.layerBand(
                e.hierarchy().layers().total()))), EstateFigures.BANDS));
        groups.put("final-image", largestFirst(grouped(parsed, Breakdowns::finalImage)));
        groups.put("footprint", largestFirst(grouped(parsed, e -> Stream.of(e.footprint().footprint()))));
        groups.put("runtime", largestFirst(grouped(parsed, e -> e.build().runtimes().stream().map(Runtimes::label)
                .distinct())));
        groups.put("unusual", largestFirst(grouped(parsed, e -> e.build().stages().stream()
                .flatMap(s -> s.unusual().stream()).distinct())));
        return groups;
    }

    static Stream<String> finalImage(CatalogueEntry entry) {
        return Stream.of("ROOT".equals(entry.image().userKind()) ? FINAL_IMAGE.get(0) : null,
                EstateFigures.lowPort(entry.image().ports()) ? FINAL_IMAGE.get(1) : null,
                EstateFigures.toolchain(entry.build()) ? FINAL_IMAGE.get(2) : null).filter(s -> s != null);
    }

    static Map<String, List<CatalogueEntry>> grouped(List<CatalogueEntry> entries,
                                                     Function<CatalogueEntry, Stream<String>> labels) {
        Map<String, List<CatalogueEntry>> groups = new LinkedHashMap<>();
        entries.forEach(entry -> labels.apply(entry).forEach(label -> groups.computeIfAbsent(label,
                l -> new java.util.ArrayList<>()).add(entry)));
        return groups;
    }

    static Map<String, List<CatalogueEntry>> ordered(Map<String, List<CatalogueEntry>> groups, List<String> order) {
        return order.stream().filter(groups::containsKey).collect(Collectors.toMap(label -> label, groups::get,
                (a, b) -> a, LinkedHashMap::new));
    }

    static Map<String, List<CatalogueEntry>> largestFirst(Map<String, List<CatalogueEntry>> groups) {
        return EstateFigures.largestFirst(counts(groups)).keySet().stream().collect(Collectors.toMap(label -> label,
                groups::get, (a, b) -> a, LinkedHashMap::new));
    }

    static Map<String, Integer> counts(Map<String, List<CatalogueEntry>> groups) {
        return groups.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size(),
                (a, b) -> a, LinkedHashMap::new));
    }
}
