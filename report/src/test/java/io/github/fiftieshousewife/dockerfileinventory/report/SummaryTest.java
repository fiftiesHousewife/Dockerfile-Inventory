package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
}
