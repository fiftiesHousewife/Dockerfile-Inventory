package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchetypeRowsTest {

    static final String UBI = "ubi-minimal:9|microdnf|git|NUMERIC||EXEC|stages=1";
    static final String SCRATCH = "scratch:|||ROOT||NONE|stages=1";

    static final Ranking RANKING = new Ranking(List.of(
            new Archetype(1, "k-scratch", 5, List.of("a2", "c1"), SCRATCH, "a2", List.of("acme/bundles", "beta/c")),
            new Archetype(2, "k-ubi", 3, List.of("a1"), UBI, "a1", List.of("acme/api", "acme/web")),
            new Archetype(3, Passes.TEMPLATES, 1, List.of("t1"), Passes.TEMPLATES, "t1", List.of("beta/y")),
            new Archetype(4, "k-debian", 1, List.of("b1"), "debian:12|||ROOT||NONE|stages=1", "b1",
                    List.of("beta/x")),
            new Archetype(5, "k-var", 1, List.of("u1"), "unresolved:|||ROOT||NONE|stages=1", "u1",
                    List.of("beta/u"))),
            11, 3, 4);

    static final Estate ESTATE = new Estate(List.of(
            Estates.ESTATE.dockerfiles().get(0), Estates.ESTATE.dockerfiles().get(1),
            Estates.ESTATE.dockerfiles().get(2),
            Estates.file("c1", new Base("scratch", null, null), "ROOT", false, "beta/c"),
            Estates.file("u1", new Base("${BASE}", null, null), "ROOT", false, "beta/u")),
            Estates.ESTATE.failures(), List.of(), Map.of());

    @Test
    void bundlesTemplatesAndUnresolvedBasesLeaveTheRankingWhichIsNumberedAfresh() {
        assertThat(ArchetypeRows.of(RANKING, ESTATE).rows())
                .extracting(ArchetypeRows.Row::rank, r -> r.archetype().rank(), ArchetypeRows.Row::name,
                        ArchetypeRows.Row::locations, ArchetypeRows.Row::files)
                .containsExactly(
                        tuple(1, 2, "ubi-minimal 9 with microdnf (git), numeric user", 3, 1),
                        tuple(2, 1, "scratch, no packages, root", 1, 1),
                        tuple(3, 4, "debian 12, no packages, root", 1, 1));
    }

    @Test
    void whatLeftTheRankingIsCountedApartByKind() {
        assertThat(ArchetypeRows.of(RANKING, ESTATE).apart()).containsExactly(
                Map.entry(ArchetypeName.BUNDLE, new ArchetypeRows.Apart(1, 4)),
                Map.entry("template, not a Dockerfile", new ArchetypeRows.Apart(1, 1)),
                Map.entry("base set by a variable", new ArchetypeRows.Apart(1, 1)));
    }

    @Test
    void eachRowCountsItsLocationsByOrganisationAndRepositoryLargestFirst() {
        ArchetypeRows.Row first = ArchetypeRows.of(RANKING, ESTATE).rows().getFirst();

        assertAll(
                () -> assertThat(first.organisations()).containsExactly(Map.entry("acme", 3)),
                () -> assertThat(first.repositories())
                        .containsExactly(Map.entry("acme/api", 2), Map.entry("acme/web", 1)));
    }

    @Test
    void coverageCountsTheFewestRowsCoveringEightyAndNinetyFivePercent() {
        ArchetypeRows rows = ArchetypeRows.of(RANKING, ESTATE);

        assertAll(
                () -> assertThat(rows.total()).isEqualTo(5),
                () -> assertThat(rows.k80()).isEqualTo(2),
                () -> assertThat(rows.k95()).isEqualTo(3));
    }

    @Test
    void anArchetypesDockerfilesOutsideTheEstateAreNotLocated() {
        assertThat(ArchetypeRows.located(RANKING.archetypes().getFirst(), Map.of("c1", List.of())))
                .containsExactly("c1");
    }

    @Test
    void anOrdinaryArchetypeKeepsItsDockerfilesThatAreNotBundles() {
        assertThat(ArchetypeRows.kept(RANKING.archetypes().getFirst(), List.of("a2", "c1"), Set.of("a2")))
                .containsExactly("c1");
    }

    @Test
    void anArchetypeOfTemplatesKeepsNoDockerfiles() {
        assertThat(ArchetypeRows.kept(RANKING.archetypes().get(2), List.of("t1"), Set.of())).isEmpty();
    }

    @Test
    void anOrdinaryArchetypeSetsApartOnlyItsBundles() {
        assertThat(ArchetypeRows.setApart(RANKING.archetypes().getFirst(), List.of("a2", "c1"), Set.of("a2")))
                .isEqualTo(Map.entry(ArchetypeName.BUNDLE, List.of("a2")));
    }

    @Test
    void anArchetypeOfTemplatesIsSetApartWhole() {
        assertThat(ArchetypeRows.setApart(RANKING.archetypes().get(2), List.of("t1"), Set.of()))
                .isEqualTo(Map.entry("template, not a Dockerfile", List.of("t1")));
    }

    @Test
    void theKindsOfArchetypeSetApartAreTemplatesUnreadableFilesAndUnresolvedBases() {
        assertAll(
                () -> assertThat(ArchetypeRows.kind(Passes.TEMPLATES)).isEqualTo("template, not a Dockerfile"),
                () -> assertThat(ArchetypeRows.kind("unresolved:|||ROOT||NONE|stages=1"))
                        .isEqualTo(ArchetypeRows.UNRESOLVED),
                () -> assertThat(ArchetypeRows.kind(UBI)).isNull());
    }

    @Test
    void eachKindSetApartCountsItsFilesAndLocationsInTheKindsOrder() {
        assertThat(ArchetypeRows.apart(Map.of(ArchetypeRows.UNRESOLVED, List.of("u1"),
                ArchetypeName.BUNDLE, List.of("a2", "zz")), Map.of("a2", List.of(location("acme/bundles")))))
                .containsExactly(Map.entry(ArchetypeName.BUNDLE, new ArchetypeRows.Apart(2, 1)),
                        Map.entry(ArchetypeRows.UNRESOLVED, new ArchetypeRows.Apart(1, 0)));
    }

    @Test
    void rowsAreRankedByLocationsThenByTheArchetypesRank() {
        ArchetypeRows.Row one = ArchetypeRows.row(0, RANKING.archetypes().get(1), List.of("a1"),
                Map.of("a1", List.of(location("acme/api"))));
        ArchetypeRows.Row two = ArchetypeRows.row(0, RANKING.archetypes().getFirst(), List.of("c1"),
                Map.of("c1", List.of(location("beta/c"))));

        assertThat(ArchetypeRows.ranked(List.of(one, two), Map.of()).rows())
                .extracting(ArchetypeRows.Row::rank, row -> row.archetype().rank())
                .containsExactly(tuple(1, 1), tuple(2, 2));
    }

    private static Location location(String repository) {
        return new Location(repository, "Dockerfile", "release");
    }
}
