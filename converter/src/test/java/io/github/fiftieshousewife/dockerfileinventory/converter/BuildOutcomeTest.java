package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuildOutcomeTest {

    private static final String MISSING = "Error: building at STEP \"ADD private-org-peribolos-sync"
            + " /usr/bin/private-org-peribolos-sync\": checking on sources under \"/src/openshift/ci-tools/4985056a\":"
            + " copier: stat: \"/private-org-peribolos-sync\": no such file or directory";
    private static final String DISK = "level=error msg=\"While applying layer: ApplyLayer stdout:  stderr: write"
            + " /opt/app-root/lib/libcufft.so.12: no space left on device exit status 1\"";

    @Test
    void aZeroExitIsBuilt() {
        assertThat(BuildOutcome.of(0, "")).isEqualTo(BuildOutcome.BUILT);
    }

    @Test
    void timeoutsExitStatusIsTimedOut() {
        assertThat(BuildOutcome.of(124, "")).isEqualTo(BuildOutcome.TIMED_OUT);
    }

    @Test
    void aFailureCheckingItsSourcesIsAMissingSource() {
        assertThat(BuildOutcome.of(125, MISSING)).isEqualTo(BuildOutcome.MISSING_SOURCE);
    }

    @Test
    void aMissingSourceFromAnotherStageIsAFailureNotAMissingSource() {
        assertThat(BuildOutcome.of(125, "Error: building at STEP \"COPY --from=builder /out/app /app\": checking on"
                + " sources under \"/var/lib/containers/storage/vfs/dir/4f2a\": copier: stat: \"/out/app\": no such"
                + " file or directory")).isEqualTo(BuildOutcome.FAILED);
    }

    @Test
    void anyOtherFailureIsFailed() {
        assertThat(BuildOutcome.of(125, DISK)).isEqualTo(BuildOutcome.FAILED);
    }
}
