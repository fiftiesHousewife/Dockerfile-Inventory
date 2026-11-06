package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** What lineage needs from Artifactory, over REST only (no AQL), so the run can be tested against a fake. */
public interface Artifactory {

    /** Keys of the local Docker repositories. */
    List<String> dockerRepositories();

    /** Image names in a repository, from the Docker v2 catalogue ({@code team/app}). */
    List<String> images(String repository);

    /** An image's tags; empty when it has none or no longer exists. */
    List<String> tags(String repository, String image);

    /** The manifest or manifest list a tag or digest names; empty when it no longer exists. */
    Optional<Manifest> manifest(String repository, String image, String reference);

    /** A blob's text (lineage only fetches image configs); empty when it no longer exists. */
    Optional<String> blob(String repository, String image, String digest);

    /** When a tag was last pushed, for the gap list; empty when the tag no longer exists. */
    Optional<Instant> lastPushed(String repository, String image, String tag);
}
