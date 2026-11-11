package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RestRepositoriesTest {

    @Test
    void readsARepositoryLeavingItsHeadCommitToBeAskedFor() {
        assertThat(RestRepositories.repository(page().get(0))).isEqualTo(new Repository("payments/ledger-api",
                "main", null, false, false, "2026-10-30T09:12:00Z"));
    }

    @Test
    void readsAnArchivedForkNeverPushed() {
        assertThat(RestRepositories.repository(page().get(1)))
                .isEqualTo(new Repository("payments/empty", "main", null, true, true, null));
    }

    @Test
    void asksForAPageOfEveryKindOfRepository() {
        assertThat(RestRepositories.path("api/v3/", "payments", 2))
                .isEqualTo("api/v3/orgs/payments/repos?per_page=100&type=all&page=2");
    }

    @Test
    void asksForABranchWithItsSlashesKept() {
        assertThat(RestRepositories.branchPath("api/v3/", "payments/ledger-api", "release/2.x"))
                .isEqualTo("api/v3/repos/payments/ledger-api/branches/release/2.x");
    }

    @Test
    void escapesWhatAPathSegmentCannotHold() {
        assertThat(RestRepositories.segments("fix #12 a+b")).isEqualTo("fix%20%2312%20a%2Bb");
    }

    @Test
    void readsTheHeadCommitOfABranch() {
        assertThat(RestRepositories.headCommit(new ObjectMapper().readTree(RecordedGitHub.fixture("rest-branch.json"))))
                .isEqualTo("1111111111111111111111111111111111111111");
    }

    private static JsonNode page() {
        return new ObjectMapper().readTree(RecordedGitHub.fixture("rest-repositories-page1.json"));
    }
}
