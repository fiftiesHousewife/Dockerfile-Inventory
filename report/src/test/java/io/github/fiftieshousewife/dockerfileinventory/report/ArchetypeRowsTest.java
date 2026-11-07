package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
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
            FiguresTest.ESTATE.dockerfiles().get(0), FiguresTest.ESTATE.dockerfiles().get(1),
            FiguresTest.ESTATE.dockerfiles().get(2),
            FiguresTest.file("c1", new Base("scratch", null, null), "ROOT", false, "beta/c"),
            FiguresTest.file("u1", new Base("${BASE}", null, null), "ROOT", false, "beta/u")),
            FiguresTest.ESTATE.failures(), List.of(), Map.of());

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
                Map.entry(ArchetypeName.BUNDLE, new Figures.Apart(1, 4)),
                Map.entry("template, not a Dockerfile", new Figures.Apart(1, 1)),
                Map.entry("base set by a variable", new Figures.Apart(1, 1)));
    }

    @Test
    void eachRowCountsItsLocationsByOrganisationAndRepositoryLargestFirst() {
        assertThat(ArchetypeRows.of(RANKING, ESTATE).rows().getFirst()).satisfies(r -> {
            assertThat(r.organisations()).containsExactly(Map.entry("acme", 3));
            assertThat(r.repositories()).containsExactly(Map.entry("acme/api", 2), Map.entry("acme/web", 1));
        });
    }

    @Test
    void withinAnOrganisationOnlyItsFilesCountAndArchetypesWithoutThemAreLeftOut() {
        ArchetypeRows beta = ArchetypeRows.of(RANKING, ESTATE.within("beta"));

        assertThat(beta.rows()).extracting(ArchetypeRows.Row::name, ArchetypeRows.Row::files)
                .containsExactly(tuple("scratch, no packages, root", 1), tuple("debian 12, no packages, root", 1));
        assertThat(beta.apart()).doesNotContainKey(ArchetypeName.BUNDLE)
                .containsEntry("template, not a Dockerfile", new Figures.Apart(1, 1));
    }

    @Test
    void coverageCountsTheFewestRowsCoveringEightyAndNinetyFivePercent() {
        assertThat(ArchetypeRows.of(RANKING, ESTATE)).satisfies(r -> {
            assertThat(r.total()).isEqualTo(5);
            assertThat(r.k80()).isEqualTo(2);
            assertThat(r.k95()).isEqualTo(3);
        });
    }
}
