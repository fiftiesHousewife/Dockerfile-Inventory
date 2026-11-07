package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.Figures.Count;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FiguresTest {

    static final Estate ESTATE = new Estate(List.of(
            file("a1", new Base("registry.example.com/ubi9/ubi-minimal", "9.4", "sha256:a"), "NUMERIC", false,
                    "acme/api", "acme/api", "acme/web"),
            file("a2", new Base("scratch", null, null), "ROOT", true,
                    "acme/bundles", "acme/bundles", "acme/bundles", "acme/bundles"),
            file("b1", new Base("debian", "12", null), "ROOT", false, "beta/x")),
            List.of(new Failure("t1", Tallies.TEMPLATE + " (jinja)", List.of(at("beta/y"))),
                    new Failure("f1", "Syntax error", List.of(at("acme/z")))),
            List.of(), Map.of());

    static final Figures.Sources SOURCES = Figures.Sources.of(ESTATE, EolList.empty());

    @Test
    void aReleaseIndexWasGivenWhenAnyLocationIsReleasedAndAnEndOfLifeListWhenAnyBaseMatchedIt() {
        assertThat(SOURCES).isEqualTo(new Figures.Sources(false, true));

        EolList eol = EolList.read(Path.of("src/test/resources/fixtures/eol.csv"));

        assertThat(Figures.Sources.of(ESTATE.within("acme"), eol))
                .isEqualTo(new Figures.Sources(true, false));
    }

    @Test
    void anOrganisationsFiguresCountItsLocationsRepositoriesFilesAndBundlesApart() {
        assertThat(Figures.of("acme", ESTATE.within("acme"), EolList.empty(), SOURCES)).satisfies(f -> {
            assertThat(f.name()).isEqualTo("acme");
            assertThat(f.locations()).isEqualTo(8);
            assertThat(f.repositories()).isEqualTo(4);
            assertThat(f.files()).isEqualTo(3);
            assertThat(f.bundles()).isEqualTo(new Figures.Apart(1, 4));
            assertThat(f.templates()).isEqualTo(new Figures.Apart(0, 0));
            assertThat(f.largestRepository()).isEqualTo("acme/bundles");
            assertThat(f.largestRepositoryLocations()).isEqualTo(4);
        });
    }

    @Test
    void usersManagersAndBaseFamiliesLeaveBundlesOut() {
        assertThat(Figures.of("acme", ESTATE.within("acme"), EolList.empty(), SOURCES)).satisfies(f -> {
            assertThat(f.users()).containsExactly(Map.entry("NUMERIC", 3));
            assertThat(f.managers()).containsExactly(Map.entry("microdnf", 3));
            assertThat(f.families()).containsExactly(Map.entry("ubi-minimal", 3));
        });
    }

    @Test
    void eachHealthCountIsAPartOfAWholeAndKnownOnlyWhereTheDataExists() {
        assertThat(Figures.of("acme", ESTATE.within("acme"), EolList.empty(), SOURCES).counts()).containsExactly(
                Map.entry(Health.PARSE_FAILURES, new Count(1, 3, true)),
                Map.entry(Health.ROOT, new Count(0, 3, true)),
                Map.entry(Health.PINNED, new Count(3, 3, true)),
                Map.entry(Health.EOL, new Count(0, 3, false)),
                Map.entry(Health.RELEASED, new Count(0, 8, true)),
                Map.entry(Health.OPAQUE, new Count(1, 2, true)));
    }

    @Test
    void templatesCountApartAndOutOfParseFailures() {
        assertThat(Figures.of("beta", ESTATE.within("beta"), EolList.empty(), SOURCES)).satisfies(f -> {
            assertThat(f.templates()).isEqualTo(new Figures.Apart(1, 1));
            assertThat(f.counts().get(Health.PARSE_FAILURES)).isEqualTo(new Count(0, 1, true));
            assertThat(f.counts().get(Health.RELEASED)).isEqualTo(new Count(1, 2, true));
        });
    }

    @Test
    void scratchIsLeftOutOfThePinnedShareAsItHasNoDigestAndEndOfLifeCountsBasesTheListMarks() {
        Estate estate = new Estate(List.of(file("e1", new Base("ubi9/ubi-minimal", "9.2", null), "ROOT", false,
                "acme/a"), file("s1", new Base("scratch", null, null), "ROOT", false, "acme/b")),
                List.of(), List.of(), Map.of());
        EolList eol = EolList.read(Path.of("src/test/resources/fixtures/eol.csv"));

        assertThat(Figures.of("acme", estate, eol, Figures.Sources.of(estate, eol)).counts()).satisfies(c -> {
            assertThat(c.get(Health.PINNED)).isEqualTo(new Count(0, 1, true));
            assertThat(c.get(Health.EOL)).isEqualTo(new Count(1, 2, true));
        });
    }

    static Dockerfile file(String sha, Base base, String user, boolean bundle, String... repositories) {
        boolean ubi = base.name().contains("ubi");
        return new Dockerfile(sha, "t-" + sha, "s-" + sha, "sig-" + sha, base, Set.of(),
                ubi ? Set.of("microdnf") : Set.of(), user, ubi ? 2 : 0, ubi ? 1 : 0,
                Arrays.stream(repositories).map(FiguresTest::at).toList(), 0, bundle);
    }

    static Location at(String repository) {
        return new Location(repository, "Dockerfile", repository.equals("beta/x") ? "RELEASED" : "DEFAULT_BRANCH");
    }
}
