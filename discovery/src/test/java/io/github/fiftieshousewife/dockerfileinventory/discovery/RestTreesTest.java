package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RestTreesTest {

    @Test
    void asksForEveryEntryWhenRecursive() {
        assertThat(RestTrees.path("v3/", "pay/api", "abc", true))
                .isEqualTo("v3/repos/pay/api/git/trees/abc?recursive=1");
    }

    @Test
    void asksForDirectChildrenOtherwise() {
        assertThat(RestTrees.path("", "pay/api", "abc", false)).isEqualTo("repos/pay/api/git/trees/abc");
    }

    @Test
    void readsEntriesAndTheTruncatedFlag() {
        Tree tree = RestTrees.tree(new ObjectMapper().readTree(RecordedGitHub.fixture("tree.json")));

        assertThat(tree).satisfies(
                t -> assertThat(t.truncated()).isFalse(),
                t -> assertThat(t.entries()).containsExactly(
                        new Tree.Entry("Dockerfile", "100644", "blob", "d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1"),
                        new Tree.Entry("src", "040000", "tree", "5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c")));
    }

    @Test
    void readsOneEntry() {
        var node = new ObjectMapper().readTree("{\"path\":\"a\",\"mode\":\"120000\",\"type\":\"blob\",\"sha\":\"s\"}");

        assertThat(RestTrees.entry(node)).isEqualTo(new Tree.Entry("a", "120000", "blob", "s"));
    }
}
