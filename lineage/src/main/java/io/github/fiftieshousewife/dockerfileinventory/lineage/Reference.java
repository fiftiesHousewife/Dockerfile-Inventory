package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;

/** Where Artifactory holds an image: repository, image name, tag, and when that tag was last pushed. */
public record Reference(String repository, String image, String tag, Instant lastPushed) {
}
