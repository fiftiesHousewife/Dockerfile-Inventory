package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sorts unlinked images into gaps and external bases, and finds images built on an outdated base. */
public final class Gaps {

    private Gaps() {
    }

    public static GapReport of(Map<String, List<Reference>> references, Map<String, Parent> parents,
                               Map<String, Link> links) {
        return new GapReport(unlinked(references, parents, links, false), unlinked(references, parents, links, true),
                outdated(references, parents));
    }

    /** Unlinked images: roots are external bases mirrored in (D1); the rest were built here from no scanned file. */
    private static List<GapRow> unlinked(Map<String, List<Reference>> references, Map<String, Parent> parents,
                                         Map<String, Link> links, boolean roots) {
        return references.entrySet().stream()
                .filter(e -> links.get(e.getKey()).tier() == Link.Tier.NONE)
                .filter(e -> parents.get(e.getKey()) instanceof Parent.Root == roots)
                .map(e -> row(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(GapRow::configDigest))
                .toList();
    }

    /**
     * Images whose parent is not the newest push of one of the parent's image names. The name leaves out the
     * repository, so a build promoted between repositories is one name (Q10). For an ambiguous parent it is enough
     * that one candidate is the newest.
     */
    private static List<Outdated> outdated(Map<String, List<Reference>> references, Map<String, Parent> parents) {
        Map<String, String> newestByName = newestByName(references);
        List<Outdated> outdated = new ArrayList<>();
        parents.forEach((image, parent) -> {
            List<String> candidates = switch (parent) {
                case Parent.Of of -> List.of(of.configDigest());
                case Parent.Ambiguous ambiguous -> ambiguous.configDigests();
                case Parent.Root root -> List.of();
            };
            candidates.stream()
                    .flatMap(c -> references.getOrDefault(c, List.of()).stream().map(Reference::image))
                    .distinct()
                    .filter(name -> !candidates.contains(newestByName.get(name)))
                    .sorted()
                    .forEach(name -> outdated.add(new Outdated(image, candidates.getFirst(), name, newestByName.get(name))));
        });
        outdated.sort(Comparator.comparing(Outdated::image).thenComparing(Outdated::imageName));
        return outdated;
    }

    /** For each image name, the config digest pushed under it most recently. */
    private static Map<String, String> newestByName(Map<String, List<Reference>> references) {
        Map<String, Reference> newest = new HashMap<>();
        Map<String, String> digests = new HashMap<>();
        references.forEach((digest, refs) -> refs.stream().filter(r -> r.lastPushed() != null).forEach(r -> {
            Reference current = newest.get(r.image());
            if (current == null || r.lastPushed().isAfter(current.lastPushed())) {
                newest.put(r.image(), r);
                digests.put(r.image(), digest);
            }
        }));
        return digests;
    }

    private static GapRow row(String configDigest, List<Reference> references) {
        Instant latest = references.stream()
                .map(Reference::lastPushed)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new GapRow(configDigest, references, latest);
    }
}
