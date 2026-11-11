package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RestHistoryTest {

    private static final ChangeRequest REQUEST = new ChangeRequest("payments/ledger-api", "abc",
            "deploy/my app/Dockerfile");

    @Test
    void asksForTheOneNewestCommitTouchingThePathFromTheScannedCommit() {
        assertThat(RestHistory.path("api/v3/", REQUEST))
                .isEqualTo("api/v3/repos/payments/ledger-api/commits?sha=abc&path=deploy%2Fmy%20app%2FDockerfile"
                        + "&per_page=1");
    }

    @Test
    void readsTheCommitAndWhenItWasCommitted() {
        assertThat(RestHistory.lastChange(REQUEST, new ObjectMapper().readTree(RecordedGitHub.fixture(
                "rest-commits.json")))).isEqualTo(new LastChange("payments/ledger-api", "abc",
                "deploy/my app/Dockerfile", "2222222222222222222222222222222222222222", "2026-09-02T10:30:00Z"));
    }

    @Test
    void aPathWithNoHistoryHasNoLastChange() {
        assertThat(RestHistory.lastChange(REQUEST, new ObjectMapper().readTree("[]")))
                .isEqualTo(new LastChange("payments/ledger-api", "abc", "deploy/my app/Dockerfile", null, null));
    }
}
