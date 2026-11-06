package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.dir;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.file;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.tree;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.truncated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.junit.jupiter.api.Test;

class TreeWalkTest {

    private static final String COMMIT = "cccccccccccccccccccccccccccccccccccccccc";

    private final FakeGitHub gitHub = new FakeGitHub();

    @Test
    void returnsTheRecursiveListingWhenItIsComplete() {
        gitHub.tree("pay/api", COMMIT, tree(file("Dockerfile", "d1")));

        assertThat(new TreeWalk(gitHub).walk("pay/api", COMMIT)).contains(tree(file("Dockerfile", "d1")));
        assertThat(gitHub.treeCalls).hasSize(1);
    }

    @Test
    void splitsATruncatedListingIntoOneListingPerSubtree() {
        gitHub.tree("pay/mono", COMMIT, truncated(file("svc/Dockerfile", "d1")))
                .listing("pay/mono", COMMIT, tree(dir("svc", "t-svc"), dir("lib", "t-lib")))
                .tree("pay/mono", "t-svc", tree(file("Dockerfile", "d1")))
                .tree("pay/mono", "t-lib", tree(file("Util.java", "j1")));

        new TreeWalk(gitHub).walk("pay/mono", COMMIT);

        assertThat(gitHub.treeRequests).containsExactly(
                "pay/mono " + COMMIT + " recursive",
                "pay/mono " + COMMIT + " listing",
                "pay/mono t-svc recursive",
                "pay/mono t-lib recursive");
    }

    @Test
    void prefixesSubtreeEntriesWithTheSubtreePath() {
        gitHub.tree("pay/mono", COMMIT, truncated(file("svc/a/Dockerfile", "d1")))
                .listing("pay/mono", COMMIT, tree(dir("svc", "t-svc")))
                .tree("pay/mono", "t-svc", tree(dir("a", "t-a"), file("a/Dockerfile", "d1")));

        assertThat(new TreeWalk(gitHub).walk("pay/mono", COMMIT))
                .contains(tree(dir("svc", "t-svc"), dir("svc/a", "t-a"), file("svc/a/Dockerfile", "d1")));
    }

    @Test
    void keepsRootFilesAndSubmodulesWithoutListingSubmodules() {
        Tree.Entry submodule = new Tree.Entry("vendor/base", "160000", "commit", "s1");
        gitHub.tree("pay/mono", COMMIT, truncated(file("Dockerfile", "d0")))
                .listing("pay/mono", COMMIT, tree(file("Dockerfile", "d0"), submodule, dir("svc", "t-svc")))
                .tree("pay/mono", "t-svc", tree(file("Dockerfile", "d1")));

        assertThat(new TreeWalk(gitHub).walk("pay/mono", COMMIT))
                .contains(tree(file("Dockerfile", "d0"), submodule, dir("svc", "t-svc"), file("svc/Dockerfile", "d1")));
        assertThat(gitHub.treeRequests).doesNotContain("pay/mono s1 recursive");
    }

    @Test
    void splitsATruncatedSubtreeOneLevelFurther() {
        gitHub.tree("pay/mono", COMMIT, truncated())
                .listing("pay/mono", COMMIT, tree(dir("svc", "t-svc")))
                .tree("pay/mono", "t-svc", truncated())
                .listing("pay/mono", "t-svc", tree(dir("a", "t-a")))
                .tree("pay/mono", "t-a", tree(file("Dockerfile", "d1")));

        assertThat(new TreeWalk(gitHub).walk("pay/mono", COMMIT))
                .contains(tree(dir("svc", "t-svc"), dir("svc/a", "t-a"), file("svc/a/Dockerfile", "d1")));
    }

    @Test
    void stopsSplittingAtTheDepthLimitAndKeepsWhatItListed() {
        gitHub.tree("pay/mono", COMMIT, truncated())
                .listing("pay/mono", COMMIT, tree(dir("svc", "t-svc")))
                .tree("pay/mono", "t-svc", truncated(file("a/Dockerfile", "d1")));

        assertThat(new TreeWalk(gitHub, 1).walk("pay/mono", COMMIT))
                .contains(new Tree(true, List.of(dir("svc", "t-svc"), file("svc/a/Dockerfile", "d1"))));
        assertThat(gitHub.treeRequests).doesNotContain("pay/mono t-svc listing");
    }

    @Test
    void failsAsAGitHubErrorWhenAListedSubtreeHasGone() {
        gitHub.tree("pay/mono", COMMIT, truncated())
                .listing("pay/mono", COMMIT, tree(dir("svc", "t-svc")));

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> new TreeWalk(gitHub).walk("pay/mono", COMMIT))
                .withMessageContaining("pay/mono").withMessageContaining("t-svc");
    }
}
