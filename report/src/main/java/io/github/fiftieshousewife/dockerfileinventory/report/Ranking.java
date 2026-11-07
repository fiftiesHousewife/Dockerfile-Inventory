package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

record Ranking(List<Archetype> archetypes, int totalLocations, int k80, int k95) {

    Ranking {
        archetypes = List.copyOf(archetypes);
    }
}
