package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

record Placement(CatalogueLinks.Links links, Footprint.Found footprint, List<Runtimes.Found> inheritedRuntimes,
                 Hierarchies.Hierarchy hierarchy, Actions actions) {

    Placement {
        inheritedRuntimes = List.copyOf(inheritedRuntimes);
    }
}
