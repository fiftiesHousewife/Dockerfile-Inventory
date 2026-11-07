package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParentTest {

    private static final Resolved.Node A = node("a");
    private static final Resolved.Node B = node("b");
    private static final Map<String, Resolved.Node> BY_DIGEST = Map.of("a", A, "b", B);

    @Test
    void anOfParentIsTheImageItNames() {
        assertThat(Parent.node(new Parent.Of("b"), BY_DIGEST)).isEqualTo(B);
    }

    @Test
    void anAmbiguousParentIsItsFirstCandidate() {
        assertThat(Parent.node(new Parent.Ambiguous(List.of("a", "b")), BY_DIGEST)).isEqualTo(A);
    }

    @Test
    void aRootOrAnUnplacedImageHasNoParentImage() {
        assertThat(Parent.node(new Parent.Root(), BY_DIGEST)).isNull();
        assertThat(Parent.node(null, BY_DIGEST)).isNull();
    }

    private static Resolved.Node node(String digest) {
        return new Resolved.Node(digest, new ImageConfig(List.of(), List.of(), null, Map.of()));
    }
}
