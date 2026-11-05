package io.github.fiftieshousewife.dockerfileinventory.discovery;

/** One row of {@code repos.jsonl}: what happened to one repository in one run. */
public record RepoOutcome(String repository, Status status, Scope scope, String commitSha,
                          boolean archived, boolean fork, String pushedAt, int dockerfiles, String detail) {

    public enum Status {
        SCANNED,
        /** No commits, so no tree. */
        EMPTY,
        /** The tree listing was cut short; the repository needs the clone fallback. */
        TRUNCATED,
        /** The release database names a commit GitHub does not have. */
        RELEASE_COMMIT_MISSING,
        FAILED;

        /** Whether a rerun should try this repository again. */
        boolean isFinal() {
            return this != FAILED;
        }
    }
}
