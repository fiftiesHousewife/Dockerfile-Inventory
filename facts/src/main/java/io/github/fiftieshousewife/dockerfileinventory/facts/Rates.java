package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * {@code rates.json}: the Phase 1 gate's measurements across every blob recorded so far. Templates saved under a
 * Dockerfile's name are counted apart and left out of the parse-failure rate, which measures the parser. Unresolved
 * {@code FROM} counts distinct blobs per organisation (D8); BuildKit features count blobs using each one (D7: use,
 * not proof that the builder supports it).
 */
public record Rates(int blobs, int templates, int parseFailures, double parseFailureRate, int runs, int opaqueRuns,
                    double opaqueRunRate, Map<String, Integer> unresolvedFromByOrganisation,
                    Map<String, Integer> buildKitFeatures, int otherShellRuns) {

    public Rates {
        unresolvedFromByOrganisation = Collections.unmodifiableMap(new TreeMap<>(unresolvedFromByOrganisation));
        buildKitFeatures = Collections.unmodifiableMap(new TreeMap<>(buildKitFeatures));
    }

    /** {@code locations}: (repository, blob SHA) pairs from discovery's manifest. */
    static Rates of(List<FactsRun.FactRow> facts, List<FactsRun.FailureRow> failures,
            List<Map.Entry<String, String>> locations) {
        int blobs = facts.size() + failures.size();
        int runs = facts.stream().mapToInt(f -> f.facts().runs()).sum();
        int opaque = facts.stream().mapToInt(f -> f.facts().opaqueRuns()).sum();
        Set<String> unresolved = facts.stream().filter(f -> !f.facts().base().resolved()).map(FactsRun.FactRow::blobSha)
                .collect(Collectors.toSet());
        Map<String, Integer> unresolvedByOrg = new TreeMap<>();
        Set<String> counted = new HashSet<>();
        locations.stream()
                .filter(l -> unresolved.contains(l.getValue()))
                .forEach(l -> {
                    String org = l.getKey().substring(0, l.getKey().indexOf('/'));
                    if (counted.add(org + "/" + l.getValue())) {
                        unresolvedByOrg.merge(org, 1, Integer::sum);
                    }
                });
        Map<String, Integer> features = new TreeMap<>();
        facts.forEach(f -> f.facts().buildKitFeatures().forEach(feature -> features.merge(feature, 1, Integer::sum)));
        int templates = (int) failures.stream().filter(f -> f.message().startsWith(FactsParser.TEMPLATE)).count();
        int parseFailures = failures.size() - templates;
        return new Rates(blobs, templates, parseFailures, ratio(parseFailures, blobs - templates),
                runs, opaque, ratio(opaque, runs),
                unresolvedByOrg, features, facts.stream().mapToInt(f -> f.facts().otherShellRuns()).sum());
    }

    private static double ratio(int part, int whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }
}
