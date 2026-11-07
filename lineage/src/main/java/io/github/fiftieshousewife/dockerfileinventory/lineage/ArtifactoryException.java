package io.github.fiftieshousewife.dockerfileinventory.lineage;

final class ArtifactoryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ArtifactoryException(String message) {
        super(message);
    }

    ArtifactoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
