package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sorts unlinked images into gaps and external bases, and finds images built on an outdated base. */
public final class Gaps {

    private Gaps() {
    }

    public static GapReport of(Map<String, List<Reference>> references, Map<String, Parent> parents,
                               Map<String, Link> links) {
        return new GapReport(unlinked(references, parents, links, false), unlinked(references, parents, links, true));
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

    private static GapRow row(String configDigest, List<Reference> references) {
        Instant latest = references.stream()
                .map(Reference::lastPushed)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new GapRow(configDigest, references, latest);
    }
}
