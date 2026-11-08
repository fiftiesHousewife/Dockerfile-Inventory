package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

final class ReleaseScope {

    private ReleaseScope() {
    }

    static String of(List<CatalogueEntry.Place> places) {
        if (places.isEmpty()) {
            return null;
        }
        return places.stream().anyMatch(place -> "RELEASED".equals(place.scope())) ? "released"
                : "default branch only";
    }
}
