package io.github.fiftieshousewife.dockerfileinventory.discovery;

/** A selection of repositories that names one the listing does not hold, or keeps none: a usage error. */
public final class SelectionNotFound extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * A selection that found nothing to select.
     *
     * @param message what was missing
     */
    public SelectionNotFound(String message) {
        super(message);
    }
}
