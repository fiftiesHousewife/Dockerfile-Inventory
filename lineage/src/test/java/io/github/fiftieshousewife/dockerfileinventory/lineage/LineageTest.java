package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LineageTest {

    @Test
    void anImageWhoseFirstLayerNoOtherImageHasIsARoot() {
        Map<String, Parent> parents = Lineage.of(List.of(node("ubi", "L1"), node("alpine", "A1")));

        assertThat(parents).containsEntry("ubi", new Parent.Root()).containsEntry("alpine", new Parent.Root());
    }

    @Test
    void theParentIsTheImageWhoseLayersAreTheLongestStrictPrefix() {
        Map<String, Parent> parents = Lineage.of(List.of(
                node("child", "L1", "L2", "L3"), node("ubi", "L1"), node("base", "L1", "L2")));

        assertThat(parents)
                .containsEntry("ubi", new Parent.Root())
                .containsEntry("base", new Parent.Of("ubi"))
                .containsEntry("child", new Parent.Of("base"));
    }

    @Test
    void siblingsThatDivergeAfterTheirParentsLayersAreNotEachOthersParent() {
        Map<String, Parent> parents = Lineage.of(List.of(
                node("base", "L1", "L2"), node("api", "L1", "L2", "A"), node("worker", "L1", "L2", "W", "W2")));

        assertThat(parents)
                .containsEntry("api", new Parent.Of("base"))
                .containsEntry("worker", new Parent.Of("base"));
    }

    private static Resolved.Node node(String digest, String... layers) {
        return node(digest, List.of(layers), List.of());
    }

    private static Resolved.Node node(String digest, List<String> layers, List<String> steps) {
        List<HistoryStep> history = steps.stream().map(s -> new HistoryStep(s, null, s.startsWith("META"), null)).toList();
        return new Resolved.Node(digest, new ImageConfig(layers, history, null, Map.of()));
    }
}
