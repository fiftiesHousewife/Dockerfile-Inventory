package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SummaryTest {

    @Test
    void theHeadlineNumbersComeFromTheEstatePassesRankingAndCatalogue() {
        Estate estate = Estate.read(EstateTest.FIXTURES, EstateTest.FIXTURES, null);
        List<Group> groups = NearDuplicates.of(Passes.signatureGroups(estate));
        Ranking ranking = Archetypes.rank(groups, estate);

        Summary summary = Summary.of(estate, Passes.exact(estate), groups.size(), ranking,
                BaseCatalogue.of(estate, EolList.empty()));

        assertThat(summary).satisfies(s -> {
            assertThat(s.locations()).isEqualTo(4);
            assertThat(s.missing()).isEqualTo(1);
            assertThat(s.passes()).extracting(PassCount::groups).containsExactly(3, 3, 3);
            assertThat(s.nearDuplicateGroups()).isEqualTo(2);
            assertThat(s.k80()).isEqualTo(ranking.k80());
            assertThat(s.parseFailureRate()).isEqualTo(1.0 / 3);
            assertThat(s.opaqueRunRate()).isZero();
            assertThat(s.pinnedShare()).isEqualTo(2.0 / 3);
            assertThat(s.managers()).isEqualTo(Map.of("microdnf", 3));
            assertThat(s.users()).isEqualTo(Map.of("NUMERIC", 2, "ROOT", 1));
            assertThat(s.effectiveUsers()).isFalse();
            assertThat(s.releasedShare()).isEqualTo(3.0 / 4);
        });
    }

    @Test
    void scratchIsLeftOutOfThePinnedShareAsItHasNoDigest() {
        List<BaseRow> bases = List.of(new BaseRow("ubi9", "9.4", "ubi9", 3, 3, "unknown", null),
                new BaseRow("scratch", null, "scratch", 5, 0, "unknown", null));

        assertThat(Summary.pinnedShare(bases)).isEqualTo(1.0);
    }

    @Test
    void theEndOfLifeShareIsOfLocationsOnBasesTheListMarksEol(@TempDir Path dir) throws IOException {
        Estate estate = Estate.read(EstateTest.FIXTURES, EstateTest.FIXTURES, null);
        Path csv = dir.resolve("eol.csv");
        Files.writeString(csv, "ubi9/ubi-minimal,9.4,eol,2026-01-01\n");

        assertThat(Summary.eolShare(BaseCatalogue.of(estate, EolList.read(csv)))).isEqualTo(1.0);
        assertThat(Summary.eolShare(List.of(new BaseRow("a", null, "a", 3, 0, "eol", null),
                new BaseRow("b", null, "b", 1, 0, "supported", null)))).isEqualTo(0.75);
    }

    @Test
    void templatesAreCountedApartAndLeftOutOfTheParseFailureRate() {
        Estate base = Estate.read(EstateTest.FIXTURES, EstateTest.FIXTURES, null);
        List<Failure> failures = new ArrayList<>(base.failures());
        failures.add(new Failure("tpl", "a template, not a Dockerfile ({{ spec.base }}); the parser said: x",
                List.of(new Location("org/tpl", "src/Dockerfile", "RELEASED"))));
        Estate estate = new Estate(base.dockerfiles(), failures, base.missing(), base.effectiveUsers());

        Summary summary = Summary.of(estate, Passes.exact(estate), 2, Archetypes.rank(Passes.signatureGroups(estate),
                estate), BaseCatalogue.of(estate, EolList.empty()));

        assertThat(summary).satisfies(s -> {
            assertThat(s.templates()).isEqualTo(1);
            assertThat(s.parseFailureRate()).isEqualTo(1.0 / 3);
        });
    }

    @Test
    void templatesAreTheFailuresWhoseMessageStartsAsFactsMarksATemplate() {
        Estate estate = new Estate(List.of(), List.of(PassesTest.failure("bad", 1),
                new Failure("tpl", Summary.TEMPLATE + " (x)", List.of())), List.of(), Map.of());

        assertThat(Summary.templates(estate)).isEqualTo(1);
    }

    @Test
    void managersCountTheLocationsOfEveryDockerfileCallingEach() {
        Estate estate = new Estate(List.of(PassesTest.dockerfile("a", "t", "s", 2),
                PassesTest.dockerfile("b", "t", "s", 3)), List.of(), List.of(), Map.of());

        assertThat(Summary.managers(estate)).isEqualTo(Map.of("microdnf", 5));
    }

    @Test
    void aDockerfilesEffectiveUserReplacesTheUserAsWritten() {
        Estate estate = new Estate(List.of(PassesTest.dockerfile("a", "t", "s", 2),
                PassesTest.dockerfile("b", "t", "s", 3)), List.of(), List.of(), Map.of("b", UserKind.NUMERIC));

        assertThat(Summary.users(estate)).isEqualTo(Map.of("ROOT", 2, "NUMERIC", 3));
    }

    @Test
    void aShareOfNothingIsZero() {
        assertThat(Summary.share(1, 4)).isEqualTo(0.25);
        assertThat(Summary.share(0, 0)).isZero();
    }
}
