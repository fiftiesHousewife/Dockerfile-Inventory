package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class Reaches {

    record Reach(int dockerfiles, int repositories, int locations, int images) {
    }

    private Reaches() {
    }

    static Reach of(String sha, List<CatalogueEntry> entries, Map<String, CatalogueLinks.Links> links,
                    int imagesBelow) {
        Set<String> below = below(sha, links);
        List<CatalogueEntry.Place> places = entries.stream().filter(entry -> below.contains(entry.blobSha()))
                .flatMap(entry -> entry.locations().stream()).toList();
        return new Reach(below.size(), (int) places.stream().map(CatalogueEntry.Place::repository).distinct().count(),
                places.size(), imagesBelow);
    }

    static Set<String> below(String sha, Map<String, CatalogueLinks.Links> links) {
        Set<String> below = new LinkedHashSet<>();
        Deque<String> next = new ArrayDeque<>(List.of(sha));
        while (!next.isEmpty()) {
            CatalogueLinks.Links found = links.get(next.pop());
            if (found != null) {
                found.children().stream().map(CatalogueLinks.Link::blobSha)
                        .filter(child -> !Objects.equals(child, sha) && below.add(child)).forEach(next::push);
            }
        }
        return below;
    }
}
