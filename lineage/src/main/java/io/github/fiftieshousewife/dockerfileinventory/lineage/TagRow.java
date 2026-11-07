package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;

record TagRow(String repository, String image, String tag, Instant lastPushed, String configDigest,
              Resolved.Reason skipped, String skipDetail) {
}
