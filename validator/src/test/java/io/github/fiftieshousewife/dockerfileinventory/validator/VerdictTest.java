package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class VerdictTest {

    private static Verdict of(String sha) {
        return Verdict.of(Validation.findings(subject(sha), V2));
    }

    @Test
    void anImageTakesTheWorstSeverityOfItsFindingsOrRunsWithNone() {
        assertAll(
                () -> assertThat(of("080b0412")).isEqualTo(Verdict.BREAKS),
                () -> assertThat(of("08bf2be5")).isEqualTo(Verdict.DRIFT),
                () -> assertThat(of("01c98292")).isEqualTo(Verdict.RUNS));
    }

    @Test
    void informationAloneLeavesAnImageRunningWithNotes() {
        assertThat(Verdict.of(List.of(new Finding(Check.P9, Severity.INFORMATION, Certainty.READ, "a", "m"))))
                .isEqualTo(Verdict.INFORMATION);
    }
}
