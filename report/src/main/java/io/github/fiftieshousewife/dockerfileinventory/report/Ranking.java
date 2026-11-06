package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

/** Archetypes by size, and how many it takes to cover 80% and 95% of the estate's locations. */
public record Ranking(List<Archetype> archetypes, int totalLocations, int k80, int k95) {

    public Ranking {
        archetypes = List.copyOf(archetypes);
    }
}
