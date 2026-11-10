package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;
import java.util.Optional;

final class RepoScan {

    record Result(RepoOutcome outcome, List<Located> dockerfiles, List<Located> yaml) {

        Result {
            dockerfiles = List.copyOf(dockerfiles);
            yaml = List.copyOf(yaml);
        }

        Result(RepoOutcome outcome, List<Located> dockerfiles) {
            this(outcome, dockerfiles, List.of());
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
        return walk.walk(repository.nameWithOwner(), commit)
                .map(tree -> scanned(repository, scope, commit, tree))
                .orElseGet(() -> noTree(repository, scope, commit));
    }

    static Result scanned(Repository repository, Scope scope, String commit, Tree tree) {
        List<Located> found = tree.entries().stream()
                .filter(RepoScan::isDockerfile)
                .map(e -> new Located(repository.nameWithOwner(), commit, scope, e.path(), e.sha()))
                .toList();
        List<Located> yaml = tree.entries().stream()
                .filter(e -> e.isBlob() && !e.isSymlink() && ManifestPaths.isCandidate(e.path()))
                .map(e -> new Located(repository.nameWithOwner(), commit, scope, e.path(), e.sha()))
                .toList();
        RepoOutcome.Status status = tree.truncated() ? RepoOutcome.Status.TRUNCATED : RepoOutcome.Status.SCANNED;
        Result result = result(repository, status, scope, commit, found, null);
        return new Result(result.outcome(), found, yaml);
    }

    static Result noTree(Repository repository, Scope scope, String commit) {
        return scope == Scope.RELEASED
                ? result(repository, RepoOutcome.Status.RELEASE_COMMIT_MISSING, scope, commit, List.of(),
                        "release database commit not found in GitHub")
                : result(repository, RepoOutcome.Status.EMPTY, scope, commit, List.of(), "no tree");
    }

    static boolean isDockerfile(Tree.Entry entry) {
        return entry.isBlob() && !entry.isSymlink() && DockerfilePaths.isDockerfile(entry.path());
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
