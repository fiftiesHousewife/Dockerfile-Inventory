package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class Reaches {

    record Reach(int dockerfiles, int repositories, int locations, int images) {
    }

    private static final CatalogueLinks.Links NO_LINKS = new CatalogueLinks.Links(List.of(), List.of(), 0);

    private Reaches() {
    }

    static Reach of(String sha, Map<String, CatalogueEntry> bySha, Map<String, CatalogueLinks.Links> links,
                    int imagesBelow) {
        Set<String> below = below(sha, links);
        List<CatalogueEntry.Place> places = below.stream().map(bySha::get).filter(Objects::nonNull)
                .flatMap(entry -> entry.locations().stream()).toList();
        return new Reach(below.size(), (int) places.stream().map(CatalogueEntry.Place::repository).distinct().count(),
                places.size(), imagesBelow);
    }

    static Set<String> below(String sha, Map<String, CatalogueLinks.Links> links) {
        Set<String> below = new LinkedHashSet<>();
        addChildren(sha, sha, links, below);
        return below;
    }

    static void addChildren(String sha, String top, Map<String, CatalogueLinks.Links> links, Set<String> below) {
        links.getOrDefault(sha, NO_LINKS).children().stream().map(CatalogueLinks.Link::blobSha)
                .filter(child -> !Objects.equals(child, top) && below.add(child))
                .forEach(child -> addChildren(child, top, links, below));
    }
}
