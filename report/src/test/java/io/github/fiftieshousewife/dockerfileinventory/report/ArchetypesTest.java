package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchetypesTest {

    @Test
    void k80AndK95AreTheFewestArchetypesReachingThoseSharesOfLocations() {
        Ranking ranking = Archetypes.rank(List.of(group("e", 3), group("a", 50), group("f", 2), group("c", 10),
                group("b", 30), group("d", 5)), PassesTest.ESTATE);

        assertThat(ranking.totalLocations()).isEqualTo(100);
        assertThat(ranking.k80()).isEqualTo(2);
        assertThat(ranking.k95()).isEqualTo(4);
        assertThat(ranking.archetypes()).extracting(Archetype::key).containsExactly("a", "b", "c", "d", "e", "f");
        assertThat(ranking.archetypes()).extracting(Archetype::rank).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void anExactBoundaryCountsAsReachedAndTiesOrderByKey() {
        Ranking ranking = Archetypes.rank(List.of(group("z", 1), group("y", 1), group("x", 1), group("w", 1),
                group("v", 1)), PassesTest.ESTATE);

        assertThat(ranking.k80()).isEqualTo(4);
        assertThat(ranking.archetypes()).extracting(Archetype::key).containsExactly("v", "w", "x", "y", "z");
    }

    @Test
    void eachArchetypeNamesItsBlobsSignatureRepresentativeAndRepositories() {
        Group sig1 = Passes.signatureGroups(PassesTest.ESTATE).getFirst();

        assertThat(Archetypes.rank(List.of(sig1), PassesTest.ESTATE).archetypes()).singleElement().satisfies(a -> {
            assertThat(a.blobShas()).containsExactly("a", "a-cosmetic", "b-same-function");
            assertThat(a.signature()).isEqualTo("ubi-minimal:9|microdnf|git|ROOT||NONE|stages=1");
            assertThat(a.representative()).isEqualTo("a");
            assertThat(a.repositories()).hasSize(6).contains("org/repo-a-0", "org/repo-b-same-function-0");
        });
    }

    static Group group(String key, int locations) {
        return new Group(key, List.of(key), locations, Set.of("git"), "ubi-minimal");
    }
}
