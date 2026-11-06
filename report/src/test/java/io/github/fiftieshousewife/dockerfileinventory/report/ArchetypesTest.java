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

    static Group group(String key, int locations) {
        return new Group(key, List.of(key), locations, Set.of("git"), "ubi-minimal");
    }
}
