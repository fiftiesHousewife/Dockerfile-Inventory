package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V1;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ValidationTest {

    @Test
    void everyCheckRunsInTheOrderOfItsId() {
        assertThat(Validation.CHECKS).extracting(PlatformCheck::id)
                .containsExactly(Arrays.stream(Check.values()).filter(check -> !check.rule()).toArray(Check[]::new));
    }

    @Test
    void eachProblemBecomesAFindingWithItsSeverityOnTheProfileAndItsCertainty() {
        assertThat(Validation.findings(subject("080b0412"), V2)).containsExactly(
                new Finding(Check.P6, Severity.BREAKS, Certainty.READ, "080b0412714becbfcf0f309de290070b3f12a619",
                        new ShellStart().problems(subject("080b0412"), V2).getFirst()),
                new Finding(Check.P8, Severity.DRIFT, Certainty.READ, "080b0412714becbfcf0f309de290070b3f12a619",
                        new UnpinnedBase().problems(subject("080b0412"), V2).getFirst()));
    }

    @Test
    void theSameProblemWeighsDifferentlyUnderEachProfile() {
        assertAll(
                () -> assertThat(Validation.findings(subject("026d8695"), V2)).extracting(Finding::severity)
                        .contains(Severity.BREAKS),
                () -> assertThat(Validation.findings(subject("026d8695"), V1)).filteredOn(f -> f.check() == Check.P5)
                        .extracting(Finding::severity).containsExactly(Severity.INFORMATION));
    }

    @Test
    void aCheckTheProfileTurnsOffFindsNothing() {
        Profile off = new Profile("off", "", List.of(), Arrays.stream(Check.values())
                .collect(Collectors.toMap(c -> c, c -> Severity.OFF)));

        assertThat(Validation.findings(subject("080b0412"), off)).isEmpty();
    }

    @Test
    void aFindingTakesItsCertaintyForThatImage() {
        assertThat(Validation.findings(subject("00b7e9b0"), V2)).filteredOn(f -> f.check() == Check.P1)
                .extracting(Finding::certainty).containsExactly(Certainty.INFERRED);
    }
}
