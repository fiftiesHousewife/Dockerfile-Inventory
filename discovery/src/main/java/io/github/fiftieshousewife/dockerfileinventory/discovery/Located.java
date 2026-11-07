package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.Objects;

record Located(String repository, String commitSha, Scope scope, String path, String blobSha) {

    Located {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(blobSha, "blobSha");
    }
}
