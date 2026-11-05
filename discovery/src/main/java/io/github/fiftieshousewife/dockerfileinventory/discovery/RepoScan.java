package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;
import java.util.Optional;

/** Walks one repository's tree at its released commit (or default branch head) and picks out the Dockerfiles. */
final class RepoScan {

    record Result(RepoOutcome outcome, List<Located> found) {

        Result {
            found = List.copyOf(found);
        }
    }

    private final TreeWalk walk;
    private final ReleaseIndex releases;

    RepoScan(GitHub gitHub, ReleaseIndex releases) {
        this(new TreeWalk(gitHub), releases);
    }

    RepoScan(TreeWalk walk, ReleaseIndex releases) {
        this.walk = walk;
        this.releases = releases;
    }

    Result scan(Repository repository) {
        Optional<String> released = releases.releasedCommit(repository.nameWithOwner());
        Scope scope = released.isPresent() ? Scope.RELEASED : Scope.DEFAULT_BRANCH;
        String commit = released.orElse(repository.headCommit());
        if (commit == null) {
            return result(repository, RepoOutcome.Status.EMPTY, scope, null, List.of(), "no commits");
        }
        Optional<Tree> tree = walk.walk(repository.nameWithOwner(), commit);
        if (tree.isEmpty()) {
            return released.isPresent()
                    ? result(repository, RepoOutcome.Status.RELEASE_COMMIT_MISSING, scope, commit, List.of(),
                            "release database commit not found in GitHub")
                    : result(repository, RepoOutcome.Status.EMPTY, scope, commit, List.of(), "no tree");
        }
        List<Located> found = tree.get().entries().stream()
                .filter(e -> e.isBlob() && DockerfilePaths.isDockerfile(e.path()))
                .map(e -> new Located(repository.nameWithOwner(), commit, scope, e.path(), e.sha()))
                .toList();
        RepoOutcome.Status status = tree.get().truncated() ? RepoOutcome.Status.TRUNCATED : RepoOutcome.Status.SCANNED;
        return result(repository, status, scope, commit, found, null);
    }

    static Result failed(Repository repository, RuntimeException e) {
        return result(repository, RepoOutcome.Status.FAILED, null, null, List.of(), e.getMessage());
    }

    private static Result result(Repository repository, RepoOutcome.Status status, Scope scope, String commit,
                                 List<Located> found, String detail) {
        return new Result(new RepoOutcome(repository.nameWithOwner(), status, scope, commit, repository.archived(),
                repository.fork(), repository.pushedAt(), found.size(), detail), found);
    }
}
