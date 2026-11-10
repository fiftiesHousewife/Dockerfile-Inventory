package io.github.fiftieshousewife.dockerfileinventory.cli;

final class UsageError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UsageError(String message) {
        super(message);
    }
}
