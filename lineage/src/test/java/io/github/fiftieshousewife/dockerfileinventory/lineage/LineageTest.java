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

    @Test
    void chainIdsStartWithTheFirstDiffIdAndHashEachNextOntoThePrevious() {
        assertThat(Lineage.chainIds(List.of("sha256:a", "sha256:b"))).satisfies(chain -> {
            assertThat(chain).hasSize(2).first().isEqualTo("sha256:a");
            assertThat(chain.get(1)).startsWith("sha256:").isNotEqualTo("sha256:b").hasSize(71);
        });
        assertThat(Lineage.chainIds(List.of())).isEmpty();
    }

    @Test
    void aStrictPrefixIsShorterAndEqualToTheStartOfTheHistory() {
        HistoryStep a = new HistoryStep("a", null, false, null);
        HistoryStep b = new HistoryStep("b", null, false, null);

        assertThat(Lineage.isStrictPrefix(List.of(a), List.of(a, b))).isTrue();
        assertThat(Lineage.isStrictPrefix(List.of(a, b), List.of(a, b))).isFalse();
        assertThat(Lineage.isStrictPrefix(List.of(b), List.of(a, b))).isFalse();
    }

    @Test
    void theClosestCandidateHasTheLongestHistoryAndATieIsAmbiguous() {
        Resolved.Node shorter = node("short", List.of("L1"), List.of("ADD base"));
        Resolved.Node longA = node("long-a", List.of("L1"), List.of("ADD base", "RUN a"));
        Resolved.Node longB = node("long-b", List.of("L1"), List.of("ADD base", "RUN a"));

        assertThat(Lineage.closest(List.of(shorter, longA))).isEqualTo(new Parent.Of("long-a"));
        assertThat(Lineage.closest(List.of(longB, shorter, longA)))
                .isEqualTo(new Parent.Ambiguous(List.of("long-a", "long-b")));
    }

    @Test
    void candidatesWithPrecedingHistoryArePreferredAndOtherwiseAllCount() {
        Resolved.Node child = node("child", List.of("L1", "L2"), List.of("ADD base", "RUN app"));
        Resolved.Node preceding = node("preceding", List.of("L1"), List.of("ADD base"));
        Resolved.Node unrelated = node("unrelated", List.of("L1"), List.of("ADD other", "RUN x"));

        assertThat(Lineage.precedingHistory(child, List.of(preceding, unrelated))).containsExactly(preceding);
        assertThat(Lineage.nearestOf(child, List.of(preceding, unrelated))).isEqualTo(new Parent.Of("preceding"));
        assertThat(Lineage.nearestOf(child, List.of(unrelated))).isEqualTo(new Parent.Of("unrelated"));
    }

    @Test
    void imagesAreIndexedByTheLastChainIdOfTheirLayersAndImagesWithoutLayersAreLeftOut() {
        Resolved.Node a = node("a", "L1");
        Resolved.Node b = node("b", "L1");
        Resolved.Node empty = node("empty");

        assertThat(Lineage.byChain(List.of(a, b, empty), Map.of("a", List.of("c1"), "b", List.of("c1"),
                "empty", List.of()))).isEqualTo(Map.of("c1", List.of(a, b)));
    }

    @Test
    void anImageWithNoChainOrNoHeldPrefixIsARoot() {
        Resolved.Node app = node("app", "L1", "L2");

        assertThat(Lineage.parent(app, List.of(), Map.of())).isEqualTo(new Parent.Root());
        assertThat(Lineage.parent(app, List.of("c1", "c2"), Map.of("c2", List.of()))).isEqualTo(new Parent.Root());
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
