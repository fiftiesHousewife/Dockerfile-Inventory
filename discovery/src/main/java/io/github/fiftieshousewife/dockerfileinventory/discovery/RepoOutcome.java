package io.github.fiftieshousewife.dockerfileinventory.discovery;

record RepoOutcome(String repository, Status status, Scope scope, String commitSha,
                   boolean archived, boolean fork, String pushedAt, int dockerfiles, String detail) {

    enum Status {
        SCANNED,
        EMPTY,
        TRUNCATED,
        RELEASE_COMMIT_MISSING,
        FAILED;

        boolean isFinal() {
            return this != FAILED;
        }
    }
}
