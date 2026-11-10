package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoadTest {

    @Test
    void gentleFillsInSlowRatesAndFewCallsAtOnceForBoth() {
        assertThat(Load.expanded(Map.of(), Optional.of("gentle"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "GHE_MAX_REQUESTS_PER_SECOND", "2", "GHE_CONCURRENCY", "2",
                "ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "5", "ARTIFACTORY_CONCURRENCY", "4"));
    }

    @Test
    void normalFillsInTheUsualRates() {
        assertThat(Load.expanded(Map.of(), Optional.of("normal"))).containsEntry("GHE_MAX_REQUESTS_PER_SECOND", "10")
                .containsEntry("GHE_CONCURRENCY", "8").containsEntry("ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "20")
                .containsEntry("ARTIFACTORY_CONCURRENCY", "16");
    }

    @Test
    void aRateOrConcurrencyGivenIsKeptAndEachServerMayHaveItsOwnLoad() {
        Map<String, String> env = Load.expanded(Map.of("GHE_MAX_REQUESTS_PER_SECOND", "3", "GHE_LOAD", "normal",
                "ARTIFACTORY_LOAD", "gentle"), Optional.empty());

        assertAll(
                () -> assertThat(env).containsEntry("GHE_MAX_REQUESTS_PER_SECOND", "3")
                        .containsEntry("GHE_CONCURRENCY", "8"),
                () -> assertThat(env).containsEntry("ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "5"));
    }

    @Test
    void theFlagWinsOverTheEnvironmentsLoad() {
        assertThat(Load.expanded(Map.of("GHE_LOAD", "normal"), Optional.of("gentle")))
                .containsEntry("GHE_MAX_REQUESTS_PER_SECOND", "2");
    }

    @Test
    void withoutALoadNothingIsFilledIn() {
        assertThat(Load.expanded(Map.of("OUT_DIR", "out"), Optional.empty())).isEqualTo(Map.of("OUT_DIR", "out"));
    }

    @Test
    void anUnknownLoadIsRefusedByName() {
        assertThatThrownBy(() -> Load.expanded(Map.of("ARTIFACTORY_LOAD", "fast"), Optional.empty()))
                .isInstanceOf(UsageError.class).hasMessage("ARTIFACTORY_LOAD is gentle or normal, not fast");
    }
}
