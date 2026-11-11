package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class GitHubRestTest {

    private static final String SHA = "1111111111111111111111111111111111111111";

    private RecordedGitHub server;

    @BeforeEach
    void start() throws IOException {
        server = new RecordedGitHub();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private GitHubRest gitHub(boolean lastChanges) {
        return new GitHubRest(GitHubApi.from(server.root() + "api/"), HttpSettings.from(Map.of()),
                TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN"), Throttle.NONE, lastChanges);
    }

    @Test
    void pagesRepositoriesUntilAnEmptyPage() {
        server.route("/api/v3/orgs/payments/repos", exchange -> RecordedGitHub.respond(exchange, 200,
                exchange.getRequestURI().getQuery().endsWith("page=1")
                        ? "rest-repositories-page1.json" : "rest-empty-page.json"));

        List<Repository> repositories = gitHub(false).repositories("payments");

        assertAll(
                () -> assertThat(repositories).extracting(Repository::nameWithOwner)
                        .containsExactly("payments/ledger-api", "payments/empty"),
                () -> assertThat(server.requests).containsExactly(
                        "GET /api/v3/orgs/payments/repos?per_page=100&type=all&page=1",
                        "GET /api/v3/orgs/payments/repos?per_page=100&type=all&page=2"));
    }

    @Test
    void asksForTheDefaultBranchsHeadCommit() {
        server.routeFixture("/api/v3/repos/payments/ledger-api/branches/main", "rest-branch.json");

        assertThat(gitHub(false).headCommit(new Repository("payments/ledger-api", "main", null, false, false, null)))
                .isEqualTo(SHA);
    }

    @Test
    void anEmptyRepositorysBranchIsNotFoundSoItHasNoHeadCommit() {
        server.route("/api/v3/repos/payments/empty/branches/main", exchange -> RecordedGitHub.respondText(exchange,
                404, "{\"message\": \"Branch not found\"}"));

        assertThat(gitHub(false).headCommit(new Repository("payments/empty", "main", null, true, true, null)))
                .isNull();
    }

    @Test
    void aRepositoryWithNoDefaultBranchIsNotAsked() {
        gitHub(false).headCommit(new Repository("payments/bare", null, null, false, false, null));

        assertThat(server.requests).isEmpty();
    }

    @Test
    void fetchesEachDistinctBlobAndLeavesOutOneNotFound() {
        server.routeFixture("/api/v3/repos/payments/ledger-api/git/blobs/aaaa", "rest-blob-text.json");
        server.route("/api/v3/repos/payments/ledger-api/git/blobs/gone", exchange -> RecordedGitHub.respondText(
                exchange, 404, "{\"message\": \"Not Found\"}"));

        List<BlobText> blobs = gitHub(false).blobs(List.of(new BlobRequest("payments/ledger-api", "aaaa"),
                new BlobRequest("payments/ledger-api", "aaaa"), new BlobRequest("payments/ledger-api", "gone")));

        assertAll(
                () -> assertThat(blobs).extracting(BlobText::sha).containsExactly("aaaa"),
                () -> assertThat(server.requests).hasSize(2));
    }

    @Test
    void readsLastChangesWhenAskedTo() {
        server.routeFixture("/api/v3/repos/payments/ledger-api/commits", "rest-commits.json");

        assertThat(gitHub(true).lastChanges(List.of(new ChangeRequest("payments/ledger-api", SHA, "Dockerfile"))))
                .extracting(LastChange::changedIn).containsExactly("2222222222222222222222222222222222222222");
    }

    @Test
    void aCommitNotFoundHasALastChangeWithNothingInIt() {
        server.route("/api/v3/repos/payments/ledger-api/commits", exchange -> RecordedGitHub.respondText(exchange,
                422, "{\"message\": \"No commit found for SHA\"}"));

        assertThat(gitHub(true).lastChanges(List.of(new ChangeRequest("payments/ledger-api", SHA, "Dockerfile"))))
                .containsExactly(new LastChange("payments/ledger-api", SHA, "Dockerfile", null, null));
    }

    @Test
    void readsNoLastChangesUnlessAskedTo() {
        gitHub(false).lastChanges(List.of(new ChangeRequest("payments/ledger-api", SHA, "Dockerfile")));

        assertThat(server.requests).isEmpty();
    }

    @Test
    void countsOneCallARequestAndNoneForLastChangesUnlessAskedTo() {
        Pending pending = new Pending(List.of(new BlobRequest("payments/api", "one")),
                List.of(new BlobRequest("payments/api", "yaml")), List.of(new ChangeRequest("payments/api", SHA,
                "Dockerfile")));

        assertAll(
                () -> assertThat(gitHub(false).calls(pending)).isEqualTo(2),
                () -> assertThat(gitHub(true).calls(pending)).isEqualTo(3));
    }
}
