package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.List;

/** An image no scanned Dockerfile matches, with every reference to it and its latest push. */
public record GapRow(String configDigest, List<Reference> references, Instant lastPushed) {

    public GapRow {
        references = List.copyOf(references);
    }
}
