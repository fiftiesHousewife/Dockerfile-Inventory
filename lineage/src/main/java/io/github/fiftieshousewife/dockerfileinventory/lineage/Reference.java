package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;

record Reference(String repository, String image, String tag, Instant lastPushed) {
}
