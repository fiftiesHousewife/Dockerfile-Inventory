package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

record Rates(int blobs, int templates, int parseFailures, double parseFailureRate, int runs, int opaqueRuns,
             double opaqueRunRate, Map<String, Integer> unresolvedFromByOrganisation,
             Map<String, Integer> buildKitFeatures, int otherShellRuns) {

    Rates {
        unresolvedFromByOrganisation = Collections.unmodifiableMap(new TreeMap<>(unresolvedFromByOrganisation));
        buildKitFeatures = Collections.unmodifiableMap(new TreeMap<>(buildKitFeatures));
    }

    static Rates of(List<FactsRun.FactRow> facts, List<FactsRun.FailureRow> failures,
            List<Map.Entry<String, String>> locations) {
        int blobs = facts.size() + failures.size();
        int runs = facts.stream().mapToInt(f -> f.facts().runs()).sum();
        int opaque = facts.stream().mapToInt(f -> f.facts().opaqueRuns()).sum();
        int templates = (int) failures.stream().filter(f -> f.message().startsWith(FactsParser.TEMPLATE)).count();
        int parseFailures = failures.size() - templates;
        return new Rates(blobs, templates, parseFailures, ratio(parseFailures, blobs - templates),
                runs, opaque, ratio(opaque, runs), unresolvedByOrganisation(facts, locations), featureCounts(facts),
                facts.stream().mapToInt(f -> f.facts().otherShellRuns()).sum());
    }

    static Map<String, Integer> unresolvedByOrganisation(List<FactsRun.FactRow> facts,
            List<Map.Entry<String, String>> locations) {
        Set<String> unresolved = facts.stream().filter(f -> !f.facts().base().resolved()).map(FactsRun.FactRow::blobSha)
                .collect(Collectors.toSet());
        Map<String, Integer> byOrganisation = new TreeMap<>();
        Set<String> counted = new HashSet<>();
        locations.stream()
                .filter(l -> unresolved.contains(l.getValue()))
                .forEach(l -> {
                    String organisation = l.getKey().substring(0, l.getKey().indexOf('/'));
                    if (counted.add(organisation + "/" + l.getValue())) {
                        byOrganisation.merge(organisation, 1, Integer::sum);
                    }
                });
        return byOrganisation;
    }

    static Map<String, Integer> featureCounts(List<FactsRun.FactRow> facts) {
        Map<String, Integer> features = new TreeMap<>();
        facts.forEach(f -> f.facts().buildKitFeatures().forEach(feature -> features.merge(feature, 1, Integer::sum)));
        return features;
    }

    static double ratio(int part, int whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }
}
