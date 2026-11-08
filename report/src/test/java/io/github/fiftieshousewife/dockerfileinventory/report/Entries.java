package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;
import java.util.Map;

// Catalogue entries for the page tests, with only what a test sets.
final class Entries {

    private Entries() {
    }

    static CatalogueEntry entry(String sha, String repository, String userKind, List<String> ports, String footprint,
                                int totalLayers, int depth, List<CatalogueEntry.PackageEntry> packages,
                                List<String> unusual, List<Runtimes.Found> runtimes,
                                List<Hierarchies.BuiltImage> images) {
        CatalogueEntry.StageEntry stage = new CatalogueEntry.StageEntry(0, null, "ubi9/ubi", "9.6", null, false,
                null, List.of(), 3, 1, 1, 0, 2, List.of(), List.of(), unusual);
        return new CatalogueEntry(sha, List.of(new CatalogueEntry.Place(repository, "Dockerfile", "RELEASED", "c1",
                null, null)), false,
                new CatalogueEntry.Image("1001", userKind, ports, null, null, null, List.of(), null, List.of(), null,
                        Map.of(), Map.of()),
                new CatalogueEntry.Build(List.of(stage), packages, List.of(), 2, 1, 0, Map.of("RUN", 1), runtimes),
                new CatalogueLinks.Links(List.of(), List.of(), depth),
                new Footprint.Found(footprint, "ubi9/ubi", List.of()),
                new Hierarchies.Hierarchy(new Hierarchies.Layers(2, totalLayers - 2, totalLayers, "declared",
                        totalLayers > 40), images, List.of(), List.of()),
                null, null);
    }

    static CatalogueEntry plain(String sha, String repository) {
        return entry(sha, repository, "NUMERIC", List.of(), "minimal", 10, 0, List.of(), List.of(), List.of(),
                List.of());
    }

    static CatalogueEntry unparsed(String sha, String repository) {
        return new CatalogueEntry(sha, List.of(new CatalogueEntry.Place(repository, "Dockerfile", "RELEASED", "c1",
                null, null)), false, null, null, null, null, null, null, "mismatched input");
    }

    static Hierarchies.Step step(String digest, String... unmaintained) {
        return new Hierarchies.Step(digest, List.of(digest + ":1"), List.of(), 1, null, null, List.of(unmaintained));
    }

    static Hierarchies.BuiltImage image(String digest, Hierarchies.Step... blockedBy) {
        return new Hierarchies.BuiltImage(digest, List.of(digest + ":1"), null, null, List.of(blockedBy),
                List.of(blockedBy), Map.of(), null, List.of(), new Cadences.Cadence(0, null, null),
                new Provenance(false, false, false), new RegistryImage.Usage(null, null));
    }
}
