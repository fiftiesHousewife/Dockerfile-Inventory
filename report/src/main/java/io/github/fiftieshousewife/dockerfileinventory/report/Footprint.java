package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        List<String> through = new ArrayList<>();
        String current = sha;
        for (List<CatalogueLinks.Link> up = parents(links, current); !up.isEmpty()
                && bySha.containsKey(up.getFirst().blobSha()) && !through.contains(up.getFirst().blobSha())
                && !up.getFirst().blobSha().equals(sha); up = parents(links, current)) {
            current = up.getFirst().blobSha();
            through.add(current);
        }
        String base = CatalogueLinks.finalBase(bySha.get(current));
        return new Found(base == null ? UNKNOWN : rules.classify(base), base, through);
    }

    private static List<CatalogueLinks.Link> parents(Map<String, CatalogueLinks.Links> links, String sha) {
        CatalogueLinks.Links found = links.get(sha);
        return found == null ? List.of() : found.parents();
    }
}
