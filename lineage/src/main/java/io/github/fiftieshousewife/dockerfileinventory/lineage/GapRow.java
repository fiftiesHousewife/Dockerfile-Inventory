package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.List;

record GapRow(String configDigest, List<Reference> references, Instant lastPushed) {

    GapRow {
        references = List.copyOf(references);
    }
}
