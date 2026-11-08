package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface Artifactory {

    List<String> dockerRepositories();

    List<String> images(String repository);

    List<String> tags(String repository, String image);

    Optional<Manifest> manifest(String repository, String image, String reference);

    Optional<String> blob(String repository, String image, String digest);

    Optional<Instant> lastPushed(String repository, String image, String tag);

    Optional<DownloadStats> stats(String repository, String image, String tag);
}
