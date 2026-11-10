package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// GitHub truncates a recursive tree listing past 100,000 entries or 7 MB.
final class TreeWalk {

    static final int MAX_DEPTH = 8;

    private final GitHub gitHub;
    private final int maxDepth;

    TreeWalk(GitHub gitHub) {
        this(gitHub, MAX_DEPTH);
    }

    TreeWalk(GitHub gitHub, int maxDepth) {
        this.gitHub = gitHub;
        this.maxDepth = maxDepth;
    }

    Optional<Tree> walk(String repository, String commitSha) {
        return gitHub.tree(repository, commitSha, true).map(whole -> complete(repository, commitSha, whole, 0));
    }

    Tree complete(String repository, String sha, Tree recursive, int splits) {
        if (!recursive.truncated() || splits >= maxDepth) {
            return recursive;
        }
        List<Tree> parts = listed(repository, sha, false).entries().stream()
                .map(child -> withSubtree(repository, child, splits))
                .toList();
        return new Tree(parts.stream().anyMatch(Tree::truncated),
                parts.stream().flatMap(part -> part.entries().stream()).toList());
    }

    Tree withSubtree(String repository, Tree.Entry child, int splits) {
        if (!child.isTree()) {
            return new Tree(false, List.of(child));
        }
        Tree subtree = complete(repository, child.sha(), listed(repository, child.sha(), true), splits + 1);
        return new Tree(subtree.truncated(),
                Stream.concat(Stream.of(child), subtree.entries().stream().map(e -> e.under(child.path()))).toList());
    }

    Tree listed(String repository, String sha, boolean recursive) {
        return gitHub.tree(repository, sha, recursive).orElseThrow(() -> new GitHubException(
                "tree %s in %s disappeared during the scan".formatted(sha, repository)));
    }
}
