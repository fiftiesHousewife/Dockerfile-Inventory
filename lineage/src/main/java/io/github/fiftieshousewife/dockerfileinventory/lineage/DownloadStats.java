package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;

// lastDownloaded is null for a tag never pulled.
record DownloadStats(long downloadCount, Instant lastDownloaded) {
}
