package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.List;

// The fields after skipDetail are null (architectures empty) in rows recorded before lineage kept them, so an old
// run still reads.
record TagRow(String repository, String image, String tag, Instant lastPushed, String configDigest,
              Resolved.Reason skipped, String skipDetail, Long size, List<String> architectures,
              String manifestDigest, Long downloads, Instant lastDownloaded) {

    TagRow {
        architectures = architectures == null ? List.of() : List.copyOf(architectures);
    }

    TagRow(String repository, String image, String tag, Instant lastPushed, String configDigest,
           Resolved.Reason skipped, String skipDetail) {
        this(repository, image, tag, lastPushed, configDigest, skipped, skipDetail, null, List.of(), null, null, null);
    }
}
