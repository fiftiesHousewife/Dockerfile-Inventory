package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record Figures(String name, int locations, int repositories, int files, Apart bundles, Apart templates,
               Map<String, Integer> users, Map<String, Integer> managers, Map<String, Integer> families,
               String largestRepository, int largestRepositoryLocations, Map<String, Count> counts) {

    record Apart(int files, int locations) {
    }

    record Count(int part, int whole, boolean known) {

        double share() {
            return whole == 0 ? 0 : (double) part / whole;
        }
    }

    Figures {
        users = Collections.unmodifiableMap(new TreeMap<>(users));
        managers = Collections.unmodifiableMap(new TreeMap<>(managers));
        families = Collections.unmodifiableMap(new TreeMap<>(families));
        counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    static Figures of(String name, Estate estate, EolList eol) {
        Estate images = estate.withoutBundles();
        List<Location> all = locations(estate);
        Map<String, Integer> byRepository = all.stream()
                .collect(Collectors.toMap(Location::repository, l -> 1, Integer::sum, TreeMap::new));
        Map.Entry<String, Integer> largest = byRepository.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(
                        Collections.reverseOrder())))
                .orElse(Map.entry("", 0));
        return new Figures(name, all.size(), byRepository.size(),
                estate.dockerfiles().size() + estate.failures().size(), bundles(estate), templates(estate),
                Summary.users(images), Summary.managers(images), families(images), largest.getKey(),
                largest.getValue(), counts(estate, images, all, BaseCatalogue.of(images, eol)));
    }

    static Apart bundles(Estate estate) {
        List<Dockerfile> bundles = estate.dockerfiles().stream().filter(Dockerfile::bundle).toList();
        return new Apart(bundles.size(), bundles.stream().mapToInt(d -> d.locations().size()).sum());
    }

    static Apart templates(Estate estate) {
        List<Failure> templates = estate.failures().stream()
                .filter(f -> Passes.kind(f).equals(Passes.TEMPLATES)).toList();
        return new Apart(templates.size(), templates.stream().mapToInt(f -> f.locations().size()).sum());
    }

    static Map<String, Integer> families(Estate images) {
        return images.dockerfiles().stream().collect(Collectors.toMap(d -> Passes.family(d.base()),
                d -> d.locations().size(), Integer::sum, TreeMap::new));
    }

    static Map<String, Count> counts(Estate estate, Estate images, List<Location> all, List<BaseRow> bases) {
        int templates = templates(estate).files();
        int users = Summary.users(images).values().stream().mapToInt(Integer::intValue).sum();
        List<BaseRow> pinnable = bases.stream().filter(b -> !b.path().equals("scratch")).toList();
        int released = (int) all.stream().filter(l -> l.scope().equals("RELEASED")).count();
        Map<String, Count> counts = new LinkedHashMap<>();
        counts.put(Health.PARSE_FAILURES, new Count(estate.failures().size() - templates,
                estate.dockerfiles().size() + estate.failures().size() - templates, true));
        counts.put(Health.ROOT, new Count(Summary.users(images).getOrDefault("ROOT", 0), users, true));
        counts.put(Health.PINNED, new Count(sum(pinnable, BaseRow::pinnedLocations),
                sum(pinnable, BaseRow::locations), true));
        counts.put(Health.EOL, new Count(sum(bases.stream().filter(b -> b.status().equals("eol")).toList(),
                BaseRow::locations), sum(bases, BaseRow::locations),
                bases.stream().anyMatch(b -> !b.status().equals("unknown"))));
        counts.put(Health.RELEASED, new Count(released, all.size(), released > 0));
        counts.put(Health.OPAQUE, new Count(sum(images.dockerfiles(), Dockerfile::opaqueRuns),
                sum(images.dockerfiles(), Dockerfile::runs), true));
        return counts;
    }

    static List<Location> locations(Estate estate) {
        return Stream.concat(estate.dockerfiles().stream().map(Dockerfile::locations),
                estate.failures().stream().map(Failure::locations)).flatMap(List::stream).toList();
    }

    private static <T> int sum(List<T> rows, ToIntFunction<T> value) {
        return rows.stream().mapToInt(value).sum();
    }
}
