package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

final class Gaps {

    private Gaps() {
    }

    static GapReport of(Map<String, List<Reference>> references, Map<String, Parent> parents,
                        Map<String, Link> links) {
        return new GapReport(unlinked(references, parents, links, false), unlinked(references, parents, links, true),
                outdated(references, parents));
    }

    static List<GapRow> unlinked(Map<String, List<Reference>> references, Map<String, Parent> parents,
                                 Map<String, Link> links, boolean roots) {
        return references.entrySet().stream()
                .filter(e -> links.get(e.getKey()).tier() == Link.Tier.NONE)
                .filter(e -> parents.get(e.getKey()) instanceof Parent.Root == roots)
                .map(e -> row(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(GapRow::configDigest))
                .toList();
    }

    static List<Outdated> outdated(Map<String, List<Reference>> references, Map<String, Parent> parents) {
        Map<String, String> newestByName = newestByName(references);
        return parents.entrySet().stream()
                .flatMap(e -> outdated(e.getKey(), candidates(e.getValue()), references, newestByName))
                .sorted(Comparator.comparing(Outdated::image).thenComparing(Outdated::imageName))
                .toList();
    }

    // For an ambiguous parent it is enough that one candidate is the newest.
    static Stream<Outdated> outdated(String image, List<String> candidates, Map<String, List<Reference>> references,
                                     Map<String, String> newestByName) {
        return candidates.stream()
                .flatMap(c -> references.getOrDefault(c, List.of()).stream().map(Reference::image))
                .distinct()
                .filter(newestByName::containsKey)
                .filter(name -> !candidates.contains(newestByName.get(name)))
                .sorted()
                .map(name -> new Outdated(image, candidates.getFirst(), name, newestByName.get(name)));
    }

    static List<String> candidates(Parent parent) {
        return switch (parent) {
            case Parent.Of of -> List.of(of.configDigest());
            case Parent.Ambiguous ambiguous -> ambiguous.configDigests();
            case Parent.Root root -> List.of();
        };
    }

    static Map<String, String> newestByName(Map<String, List<Reference>> references) {
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

    static GapRow row(String configDigest, List<Reference> references) {
        Instant latest = references.stream()
                .map(Reference::lastPushed)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new GapRow(configDigest, references, latest);
    }
}
