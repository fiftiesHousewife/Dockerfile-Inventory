package io.github.fiftieshousewife.dockerfileinventory.lineage;

final class AccessRefused extends ArtifactoryException {

    private static final long serialVersionUID = 1L;

    AccessRefused(String message) {
        super(message);
    }
}
