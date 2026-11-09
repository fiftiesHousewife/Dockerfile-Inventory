package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FixesTest {

    private static final Catalogue ARCHETYPE = new Catalogue(Map.of("a", "k1", "b", "k1"), Map.of("k1", "b"), Map.of());

    @Test
    void theSameCheckOnAnArchetypesCopiesIsOneFixToItsRepresentative() {
        List<Fix> fixes = Fixes.of(List.of(action(Check.P1, "a", 1), action(Check.P1, "b", 1)), ARCHETYPE, Map.of(),
                Map.of());

        assertAll(
                () -> assertThat(fixes).hasSize(1),
                () -> assertThat(fixes.getFirst().representative()).isEqualTo("b"),
                () -> assertThat(fixes.getFirst().copies()).containsExactly("a", "b"));
    }

    @Test
    void whenTheRepresentativeHasNoSuchActionTheCopyWithTheMostLocationsStandsForTheArchetype() {
        List<Fix> fixes = Fixes.of(List.of(action(Check.P3, "a", 2)), ARCHETYPE, Map.of(), Map.of());

        assertThat(fixes.getFirst().representative()).isEqualTo("a");
    }

    @Test
    void aDockerfileInNoArchetypeIsAFixOfItsOwn() {
        List<Fix> fixes = Fixes.of(List.of(action(Check.P1, "z", 1)), ARCHETYPE, Map.of(), Map.of());

        assertThat(fixes.getFirst().representative()).isEqualTo("z");
    }

    @Test
    void aFixReachesTheDockerfilesBuiltOnItThatInheritWhatItChangesAndTheirLocations() {
        Catalogue built = new Catalogue(Map.of(), Map.of(), Map.of("a", List.of("c"), "c", List.of("d")));
        Map<String, Subject> subjects = Map.of("c", Fixtures.subject("03cab6f0"), "d", Fixtures.subject("080b0412"));
        Map<String, List<Location>> locations = Map.of("a", List.of(new Location("team/a", "Dockerfile")),
                "c", List.of(new Location("team/c", "Dockerfile"), new Location("team/c", "web/Dockerfile")),
                "d", List.of(new Location("team/d", "Dockerfile")));

        Fix userFix = Fixes.of(List.of(action(Check.P1, "a", 1)), built, subjects, locations).getFirst();
        Fix pinFix = Fixes.of(List.of(action(Check.P8, "a", 1)), built, subjects, locations).getFirst();

        assertAll(
                () -> assertThat(userFix.inheriting()).containsExactly("c"),
                () -> assertThat(userFix.locations()).isEqualTo(3),
                () -> assertThat(pinFix.inheriting()).isEmpty(),
                () -> assertThat(pinFix.locations()).isEqualTo(1));
    }

    @Test
    void fixesAreOrderedBySeverityThenTheLocationsTheyReach() {
        Action drift = new Action(Check.P8, Severity.DRIFT, Certainty.READ, "pin", "x", List.of("why"),
                List.of(new Location("t/x", "1"), new Location("t/x", "2"), new Location("t/x", "3")));
        Map<String, List<Location>> locations = Map.of("x", drift.where(), "y", action(Check.P1, "y", 1).where(),
                "z", action(Check.P3, "z", 2).where());

        List<Fix> fixes = Fixes.of(List.of(drift, action(Check.P1, "y", 1), action(Check.P3, "z", 2)),
                Catalogue.NONE, Map.of(), locations);

        assertThat(fixes).extracting(Fix::representative).containsExactly("z", "y", "x");
    }

    @Test
    void aFixIsAsSureAsItsSurestCopy() {
        Action inferred = new Action(Check.P1, Severity.BREAKS, Certainty.INFERRED, "user", "a", List.of("why"),
                List.of());

        assertThat(Fixes.of(List.of(inferred, action(Check.P1, "b", 1)), ARCHETYPE, Map.of(), Map.of()).getFirst()
                .certainty()).isEqualTo(Certainty.READ);
    }

    @Test
    void aFixGivesItsRepresentativesFindingsAsTheReason() {
        Action representative = new Action(Check.P1, Severity.BREAKS, Certainty.READ, "user", "b",
                List.of("runs as root"), List.of());

        assertThat(Fixes.of(List.of(action(Check.P1, "a", 1), representative), ARCHETYPE, Map.of(), Map.of())
                .getFirst().because()).containsExactly("runs as root");
    }

    static Action action(Check check, String blobSha, int locations) {
        return new Action(check, Severity.BREAKS, Certainty.READ, Actions.WHAT.get(check), blobSha, List.of("why"),
                IntStream.range(0, locations).mapToObj(i -> new Location("team/" + blobSha, "D" + i))
                        .toList());
    }

    @Test
    void theInheritingDockerfilesAreEveryGenerationOnceSortedAndNotTheCopies() {
        Catalogue built = new Catalogue(Map.of(), Map.of(), Map.of("a", List.of("d", "c"), "c", List.of("a", "d")));
        Map<String, Subject> subjects = Map.of("a", Fixtures.subject("03cab6f0"), "c", Fixtures.subject("03cab6f0"),
                "d", Fixtures.subject("03cab6f0"));

        assertThat(Fixes.inheriting(Check.P3, List.of("a"), built, subjects)).containsExactly("c", "d");
    }
}
