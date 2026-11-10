package io.github.fiftieshousewife.dockerfileinventory.discovery;

/** A run stopped on purpose after its first pass: declined, or over the ceiling on Dockerfiles. */
public final class RunStopped extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * A run stopped on purpose.
     *
     * @param message why, and what was kept
     */
    public RunStopped(String message) {
        super(message);
    }
}
