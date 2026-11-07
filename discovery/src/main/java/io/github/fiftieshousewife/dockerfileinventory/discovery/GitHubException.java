package io.github.fiftieshousewife.dockerfileinventory.discovery;

class GitHubException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    GitHubException(String message) {
        super(message);
    }

    GitHubException(String message, Throwable cause) {
        super(message, cause);
    }
}
