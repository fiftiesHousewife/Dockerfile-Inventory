package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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

    @Test
    void aDownloadThatFailedOnTheNetworkIsAFetchFailure() {
        assertAll(
                () -> assertThat(BuildOutcome.fetchFailed("WARNING: fetching https://dl-cdn.alpinelinux.org/alpine/"
                        + "v3.23/main/aarch64/APKINDEX.tar.gz: TLS: unspecified error")).isTrue(),
                () -> assertThat(BuildOutcome.fetchFailed("E: Failed to fetch http://deb.debian.org/debian/pool/main/k/"
                        + "krb5/libkrb5-3_1.20.1-2%2bdeb12u5_arm64.deb  Hash Sum mismatch")).isTrue(),
                () -> assertThat(BuildOutcome.fetchFailed("curl: (6) Could not resolve host: example.com")).isTrue(),
                () -> assertThat(BuildOutcome.fetchFailed("dial tcp 1.2.3.4:443: i/o timeout")).isTrue());
    }

    @Test
    void aBuildThatFailedOnItsOwnIsNot() {
        assertAll(
                () -> assertThat(BuildOutcome.fetchFailed("main.c:12: error: expected ';'")).isFalse(),
                () -> assertThat(BuildOutcome.fetchFailed(MISSING)).isFalse(),
                () -> assertThat(BuildOutcome.fetchFailed(DISK)).isFalse());
    }
}
