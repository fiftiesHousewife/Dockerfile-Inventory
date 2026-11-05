package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lists a repository's whole tree at a commit. GitHub truncates a recursive listing past 100,000 entries or 7 MB;
 * a truncated tree is listed one level down instead, and each subtree recursively, splitting again where needed.
 */
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

    /** {@code depth} counts the splits above this tree; past {@code maxDepth} a truncated listing is kept as it is. */
    private Tree complete(String repository, String sha, Tree recursive, int depth) {
        if (!recursive.truncated() || depth >= maxDepth) {
            return recursive;
        }
        boolean truncated = false;
        List<Tree.Entry> entries = new ArrayList<>();
        for (Tree.Entry child : listed(repository, sha, false).entries()) {
            entries.add(child);
            if (child.isTree()) {
                Tree subtree = complete(repository, child.sha(), listed(repository, child.sha(), true), depth + 1);
                truncated |= subtree.truncated();
                subtree.entries().forEach(e -> entries.add(e.under(child.path())));
            }
        }
        return new Tree(truncated, entries);
    }

    /** A tree the walk already knows exists; its absence means the repository changed under the scan. */
    private Tree listed(String repository, String sha, boolean recursive) {
        return gitHub.tree(repository, sha, recursive).orElseThrow(() -> new GitHubException(
                "tree %s in %s disappeared during the scan".formatted(sha, repository)));
    }
}
