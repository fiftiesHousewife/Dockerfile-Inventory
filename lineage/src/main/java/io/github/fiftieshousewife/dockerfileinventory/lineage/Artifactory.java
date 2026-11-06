package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

/** What lineage needs from Artifactory, over REST only (no AQL), so the run can be tested against a fake. */
public interface Artifactory {

    /** Keys of the local Docker repositories. */
    List<String> dockerRepositories();

    /** Image names in a repository, from the Docker v2 catalogue ({@code team/app}). */
    List<String> images(String repository);

    /** An image's tags; empty when it has none or no longer exists. */
    List<String> tags(String repository, String image);
}
