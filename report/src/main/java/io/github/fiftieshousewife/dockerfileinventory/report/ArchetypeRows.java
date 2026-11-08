package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// The archetypes as the pages rank them: bundles, templates, unreadable files and unresolved bases left out.
record ArchetypeRows(List<Row> rows, Map<String, Apart> apart, int total, int k80, int k95) {

    static final String UNRESOLVED = "base set by a variable";
    private static final List<String> KINDS_APART = List.of(ArchetypeName.BUNDLE,
            ArchetypeName.of(Passes.TEMPLATES), ArchetypeName.of(Passes.PARSE_FAILURES), UNRESOLVED);

    record Apart(int files, int locations) {
    }

    record Row(int rank, Archetype archetype, String name, List<String> blobShas, List<Location> where) {

        Row {
            blobShas = List.copyOf(blobShas);
            where = List.copyOf(where);
        }

        int files() {
            return blobShas.size();
        }

        int locations() {
            return where.size();
        }

        Map<String, Integer> organisations() {
            return counted(where, Location::organisation);
        }

        Map<String, Integer> repositories() {
            return counted(where, Location::repository);
        }
    }

    ArchetypeRows {
        rows = List.copyOf(rows);
        apart = Collections.unmodifiableMap(new LinkedHashMap<>(apart));
    }

    static ArchetypeRows of(Ranking ranking, Estate estate) {
        Map<String, List<Location>> locations = ReportRun.locations(estate);
        Set<String> bundles = estate.dockerfiles().stream().filter(Dockerfile::bundle).map(Dockerfile::blobSha)
                .collect(Collectors.toSet());
        Map<String, List<String>> apartBlobs = new HashMap<>();
        List<Row> kept = new ArrayList<>();
        ranking.archetypes().forEach(a -> {
            String kind = kind(a.signature());
            List<String> here = a.blobShas().stream().filter(locations::containsKey).toList();
            List<String> blobs = kind == null ? here.stream().filter(b -> !bundles.contains(b)).toList() : List.of();
            add(apartBlobs, kind == null ? ArchetypeName.BUNDLE : kind,
                    kind == null ? here.stream().filter(bundles::contains).toList() : here);
            if (!blobs.isEmpty()) {
                kept.add(row(0, a, blobs, locations));
            }
        });
        return ranked(kept, apart(apartBlobs, locations));
    }

    static String kind(String signature) {
        if (signature.equals(Passes.TEMPLATES) || signature.equals(Passes.PARSE_FAILURES)) {
            return ArchetypeName.of(signature);
        }
        return signature.startsWith(ArchetypeName.UNRESOLVED_BASE + ":") ? UNRESOLVED : null;
    }

    private static void add(Map<String, List<String>> apart, String kind, List<String> blobs) {
        if (!blobs.isEmpty()) {
            apart.computeIfAbsent(kind, k -> new ArrayList<>()).addAll(blobs);
        }
    }

    static Row row(int rank, Archetype archetype, List<String> blobs, Map<String, List<Location>> locations) {
        List<Location> where = blobs.stream().flatMap(b -> locations.getOrDefault(b, List.of()).stream()).toList();
        return new Row(rank, archetype, ArchetypeName.of(archetype.signature()), blobs, where);
    }

    static Map<String, Integer> counted(List<Location> where, Function<Location, String> key) {
        return where.stream().collect(Collectors.toMap(key, l -> 1, Integer::sum)).entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<String, Apart> apart(Map<String, List<String>> blobs, Map<String, List<Location>> locations) {
        Map<String, Apart> apart = new LinkedHashMap<>();
        KINDS_APART.stream().filter(blobs::containsKey).forEach(kind -> apart.put(kind, new Apart(
                blobs.get(kind).size(), blobs.get(kind).stream()
                .mapToInt(b -> locations.getOrDefault(b, List.of()).size()).sum())));
        return apart;
    }

    private static ArchetypeRows ranked(List<Row> kept, Map<String, Apart> apart) {
        List<Row> sorted = kept.stream().sorted(Comparator.comparingInt(Row::locations).reversed()
                .thenComparingInt(r -> r.archetype().rank())).toList();
        List<Row> rows = IntStream.range(0, sorted.size()).mapToObj(i -> renumbered(sorted.get(i), i + 1)).toList();
        int total = rows.stream().mapToInt(Row::locations).sum();
        return new ArchetypeRows(rows, apart, total, covering(rows, total, 80), covering(rows, total, 95));
    }

    private static Row renumbered(Row row, int rank) {
        return new Row(rank, row.archetype(), row.name(), row.blobShas(), row.where());
    }

    static int covering(List<Row> rows, int total, int percent) {
        long covered = 0;
        for (Row row : rows) {
            covered += row.locations();
            if (covered * 100 >= (long) percent * total) {
                return row.rank();
            }
        }
        return rows.size();
    }
}
