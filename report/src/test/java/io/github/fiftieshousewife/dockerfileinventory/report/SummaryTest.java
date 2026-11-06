package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        assertThat(summary.locations()).isEqualTo(4);
        assertThat(summary.missing()).isEqualTo(1);
        assertThat(summary.passes()).extracting(PassCount::groups).containsExactly(3, 3, 3);
        assertThat(summary.nearDuplicateGroups()).isEqualTo(2);
        assertThat(summary.k80()).isEqualTo(ranking.k80());
        assertThat(summary.parseFailureRate()).isEqualTo(1.0 / 3);
        assertThat(summary.opaqueRunRate()).isZero();
        assertThat(summary.pinnedShare()).isEqualTo(2.0 / 3);
        assertThat(summary.managers()).isEqualTo(Map.of("microdnf", 3));
        assertThat(summary.users()).isEqualTo(Map.of("NUMERIC", 2, "ROOT", 1));
        assertThat(summary.effectiveUsers()).isFalse();
        assertThat(summary.releasedShare()).isEqualTo(3.0 / 4);
    }

    @Test
    void theEndOfLifeShareIsOfLocationsOnBasesTheListMarksEol(@TempDir Path dir) throws IOException {
        Estate estate = Estate.read(EstateTest.FIXTURES, EstateTest.FIXTURES, null);
        Path csv = dir.resolve("eol.csv");
        Files.writeString(csv, "ubi9/ubi-minimal,9.4,eol,2026-01-01\n");
        List<BaseRow> bases = BaseCatalogue.of(estate, EolList.read(csv));

        Summary summary = Summary.of(estate, Passes.exact(estate), 2,
                Archetypes.rank(Passes.signatureGroups(estate), estate), bases);

        assertThat(summary.eolShare()).isEqualTo(1.0);
    }

    @Test
    void templatesAreCountedApartAndLeftOutOfTheParseFailureRate() {
        Estate base = Estate.read(EstateTest.FIXTURES, EstateTest.FIXTURES, null);
        List<Failure> failures = new java.util.ArrayList<>(base.failures());
        failures.add(new Failure("tpl", "a template, not a Dockerfile ({{ spec.base }}); the parser said: x",
                List.of(new Location("org/tpl", "src/Dockerfile", "RELEASED"))));
        Estate estate = new Estate(base.dockerfiles(), failures, base.missing(), base.effectiveUsers());

        Summary summary = Summary.of(estate, Passes.exact(estate), 2, Archetypes.rank(Passes.signatureGroups(estate),
                estate), BaseCatalogue.of(estate, EolList.empty()));

        assertThat(summary.templates()).isEqualTo(1);
        assertThat(summary.parseFailureRate()).isEqualTo(1.0 / 3);
    }
}
