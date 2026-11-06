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

    private static Resolved.Node node(String digest, String... layers) {
        return node(digest, List.of(layers), List.of());
    }

    private static Resolved.Node node(String digest, List<String> layers, List<String> steps) {
        List<HistoryStep> history = steps.stream().map(s -> new HistoryStep(s, null, s.startsWith("META"), null)).toList();
        return new Resolved.Node(digest, new ImageConfig(layers, history, null, Map.of()));
    }
}
