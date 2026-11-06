package io.github.fiftieshousewife.dockerfileinventory.lineage;

/** Artifactory answered with something the run cannot use. */
public final class ArtifactoryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ArtifactoryException(String message) {
        super(message);
    }

    public ArtifactoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
