package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class RuleRates {

    private RuleRates() {
    }

    static Map<String, Object> of(List<Finding> findings, Map<String, List<Location>> locations,
                                  List<String> skipped) {
        Map<String, Map<String, Integer>> rules = new TreeMap<>();
        findings.stream().collect(Collectors.groupingBy(Finding::rule))
                .forEach((rule, list) -> rules.put(rule, counts(list, locations)));
        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("skipped", new TreeSet<>(skipped));
        rates.put("rules", rules);
        return rates;
    }

    static Map<String, Integer> counts(List<Finding> findings, Map<String, List<Location>> locations) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("findings", findings.size());
        counts.put("files", (int) findings.stream().map(Finding::blobSha).distinct().count());
        counts.put("locations", findings.stream()
                .mapToInt(f -> locations.getOrDefault(f.blobSha(), List.of()).size()).sum());
        return counts;
    }
}
