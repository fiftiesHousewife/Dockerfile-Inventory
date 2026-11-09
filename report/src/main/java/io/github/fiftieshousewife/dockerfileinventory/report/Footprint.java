package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class Footprint {

    static final String UNKNOWN = "unknown";

    record Found(String footprint, String base, List<String> through) {

        Found {
            through = List.copyOf(through);
        }
    }

    private Footprint() {
    }

    static Found through(String sha, Map<String, CatalogueEntry> bySha, Map<String, CatalogueLinks.Links> links,
                         FootprintRules rules) {
        List<String> through = ancestors(sha, bySha, links);
        String base = CatalogueLinks.finalBase(bySha.get(through.isEmpty() ? sha : through.getLast()));
        return new Found(base == null ? UNKNOWN : rules.classify(base), base, through);
    }

    static List<String> ancestors(String sha, Map<String, CatalogueEntry> bySha,
                                  Map<String, CatalogueLinks.Links> links) {
        Set<String> seen = new HashSet<>(Set.of(sha));
        return Stream.iterate(firstParent(links, sha),
                parent -> parent != null && bySha.containsKey(parent) && seen.add(parent),
                parent -> firstParent(links, parent)).toList();
    }

    static String firstParent(Map<String, CatalogueLinks.Links> links, String sha) {
        CatalogueLinks.Links found = links.get(sha);
        return found == null || found.parents().isEmpty() ? null : found.parents().getFirst().blobSha();
    }
}
