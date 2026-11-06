package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;

/** One row of {@code images.jsonl}: a tag, and the config it names or why it was not read. */
public record TagRow(String repository, String image, String tag, Instant lastPushed, String configDigest,
                     Resolved.Reason skipped, String skipDetail) {
}
