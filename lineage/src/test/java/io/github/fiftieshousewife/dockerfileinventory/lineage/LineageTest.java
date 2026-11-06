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

    @Test
    void aChildThatAddsOnlyMetadataTakesTheImageWithTheSameLayersWhoseHistoryPrecedesItsOwn() {
        Map<String, Parent> parents = Lineage.of(List.of(
                node("app", List.of("L1", "L2"), List.of("ADD base", "RUN install", "META USER 1001")),
                node("base", List.of("L1", "L2"), List.of("ADD base", "RUN install"))));

        assertThat(parents)
                .containsEntry("app", new Parent.Of("base"))
                .containsEntry("base", new Parent.Root());
    }

    @Test
    void twinsWithTheSameLayersAndHistoryAreNotEachOthersParentAndTheirChildIsAmbiguous() {
        List<String> history = List.of("ADD base", "RUN install");
        Map<String, Parent> parents = Lineage.of(List.of(
                node("twin-b", List.of("L1", "L2"), history),
                node("twin-a", List.of("L1", "L2"), history),
                node("child", List.of("L1", "L2", "L3"), List.of("ADD base", "RUN install", "RUN app"))));

        assertThat(parents)
                .containsEntry("twin-a", new Parent.Root())
                .containsEntry("twin-b", new Parent.Root())
                .containsEntry("child", new Parent.Ambiguous(List.of("twin-a", "twin-b")));
    }

    @Test
    void amongSameLayeredCandidatesTheParentIsTheOneWhoseHistoryLeadsToThisImage() {
        Map<String, Parent> parents = Lineage.of(List.of(
                node("base", List.of("L1"), List.of("ADD base")),
                node("labelled", List.of("L1"), List.of("ADD base", "META LABEL team=x")),
                node("on-labelled", List.of("L1", "L2"), List.of("ADD base", "META LABEL team=x", "RUN app")),
                node("on-base", List.of("L1", "L3"), List.of("ADD base", "RUN other"))));

        assertThat(parents)
                .containsEntry("labelled", new Parent.Of("base"))
                .containsEntry("on-labelled", new Parent.Of("labelled"))
                .containsEntry("on-base", new Parent.Of("base"));
    }

    @Test
    void anImageWithNoLayersIsARootAndNoImagesParent() {
        Map<String, Parent> parents = Lineage.of(List.of(
                node("empty", List.of(), List.of("META LABEL x=y")),
                node("app", List.of("L1"), List.of("META LABEL x=y", "ADD app"))));

        assertThat(parents)
                .containsEntry("empty", new Parent.Root())
                .containsEntry("app", new Parent.Root());
    }

    private static Resolved.Node node(String digest, String... layers) {
        return node(digest, List.of(layers), List.of());
    }

    private static Resolved.Node node(String digest, List<String> layers, List<String> steps) {
        List<HistoryStep> history = steps.stream()
                .map(s -> new HistoryStep(s, null, s.startsWith("META"), null))
                .toList();
        return new Resolved.Node(digest, new ImageConfig(layers, history, null, Map.of()));
    }
}
