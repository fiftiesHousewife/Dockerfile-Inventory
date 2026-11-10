package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;

record DownloadStats(long downloadCount, Instant lastDownloaded) {
}
