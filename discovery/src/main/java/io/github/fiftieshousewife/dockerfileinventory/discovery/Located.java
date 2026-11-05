package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.Objects;

/**
 * One Dockerfile found in one repository at one commit. The blob SHA is the deduplication key:
 * copy-pasted Dockerfiles share it, so their content is fetched and parsed once.
 */
public record Located(String repository, String commitSha, Scope scope, String path, String blobSha) {

    public Located {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(blobSha, "blobSha");
    }
}
