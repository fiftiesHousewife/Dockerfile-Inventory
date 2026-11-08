package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseLagsTest {

    private static final BaseLags LAGS = new BaseLags(Map.of(
            "ubi9/ubi-minimal", List.of("9.4", "9.6", "latest", "9.5"),
            "ubi9/openjdk-21", List.of("1.20-2", "1.20-10", "1.21")));

    @Test
    void aNewerTagOfTheSameShapeIsTheBasesLag() {
        assertThat(LAGS.of("registry.access.redhat.com/ubi9/ubi-minimal:9.4"))
                .contains(new BaseLags.BaseLag("ubi9/ubi-minimal", "9.4", "9.6"));
    }

    @Test
    void numbersAreComparedAsNumbersNotText() {
        assertThat(LAGS.of("ubi9/openjdk-21:1.20-2")).contains(new BaseLags.BaseLag("ubi9/openjdk-21", "1.20-2",
                "1.20-10"));
    }

    @Test
    void theNewestTagHasNoLag() {
        assertThat(LAGS.of("registry.access.redhat.com/ubi9/ubi-minimal:9.6")).isEmpty();
    }

    @Test
    void aTagWithoutANumberIsNotCompared() {
        assertThat(LAGS.of("ubi9/ubi-minimal:latest")).isEmpty();
    }

    @Test
    void aBaseTheRegistryDoesNotHoldHasNoLag() {
        assertThat(LAGS.of("docker.io/library/debian:12")).isEmpty();
    }

    @Test
    void aBasePinnedByDigestIsComparedByItsTag() {
        assertThat(LAGS.of("ubi9/ubi-minimal:9.5@sha256:" + "a".repeat(64)))
                .contains(new BaseLags.BaseLag("ubi9/ubi-minimal", "9.5", "9.6"));
    }

    @Test
    void aTagWithAVeryLongNumberIsComparedWithoutOverflowing() {
        BaseLags lags = new BaseLags(Map.of("team/app", List.of("12345678901234567890123", "12345678901234567890124")));

        assertThat(lags.of("team/app:12345678901234567890123"))
                .contains(new BaseLags.BaseLag("team/app", "12345678901234567890123", "12345678901234567890124"));
    }
}
