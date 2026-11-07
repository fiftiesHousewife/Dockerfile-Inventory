package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class Samples {

    static final int SIZE = 50;
    static final String HEADER = "rule,severity,repository,path,blob_sha,line,message,verdict\r\n";
    private static final long SEED = 20261106L;

    private Samples() {
    }

    static Map<String, String> of(List<Finding> findings, Map<String, List<Location>> locations) {
        Map<String, String> samples = new TreeMap<>();
        findings.stream().collect(Collectors.groupingBy(Finding::rule))
                .forEach((rule, list) -> samples.put(rule, sample(list, locations)));
        return samples;
    }

    static String sample(List<Finding> findings, Map<String, List<Location>> locations) {
        return HEADER + String.join("", roundRobin(shuffled(byOrganisation(findings, locations)).values(), SIZE));
    }

    static Map<String, List<String>> byOrganisation(List<Finding> findings, Map<String, List<Location>> locations) {
        Map<String, List<String>> rows = new TreeMap<>();
        findings.forEach(f -> locations.getOrDefault(f.blobSha(), List.of()).forEach(l -> rows
                .computeIfAbsent(l.organisation(), o -> new ArrayList<>()).add(row(f, l))));
        return rows;
    }

    static Map<String, List<String>> shuffled(Map<String, List<String>> byOrganisation) {
        Random random = new Random(SEED);
        byOrganisation.values().forEach(rows -> {
            Collections.sort(rows);
            Collections.shuffle(rows, random);
        });
        return byOrganisation;
    }

    static List<String> roundRobin(Collection<List<String>> groups, int limit) {
        int longest = groups.stream().mapToInt(List::size).max().orElse(0);
        return IntStream.range(0, longest).boxed()
                .flatMap(round -> groups.stream().filter(rows -> round < rows.size()).map(rows -> rows.get(round)))
                .limit(limit)
                .toList();
    }

    static String row(Finding f, Location l) {
        return Csv.row(f.rule(), f.severity().name(), l.repository(), l.path(), f.blobSha(), Csv.orEmpty(f.line()),
                f.message(), "");
    }
}
