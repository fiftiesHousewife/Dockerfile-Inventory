package io.github.fiftieshousewife.dockerfileinventory.discovery;

/** A GitHub call that failed after retries, or returned GraphQL errors. */
public class GitHubException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GitHubException(String message) {
        super(message);
    }

    public GitHubException(String message, Throwable cause) {
        super(message, cause);
    }
}
