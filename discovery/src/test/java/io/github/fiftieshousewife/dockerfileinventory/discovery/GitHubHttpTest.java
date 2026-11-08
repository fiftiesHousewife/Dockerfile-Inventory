package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GitHubHttpTest {

    private static final String TREE = "/api/v3/repos/payments/ledger-api/git/trees/abc";

    private RecordedGitHub server;
    private GitHubHttp gitHub;

    @BeforeEach
    void start() throws IOException {
        server = new RecordedGitHub();
        gitHub = new GitHubHttp(GitHubApi.from(server.root() + "api/"), HttpSettings.from(Map.of()),
                TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN"));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void pagesOrganizationsBySinceUntilAnEmptyPage() {
        server.route("/api/v3/organizations", exchange -> RecordedGitHub.respond(exchange, 200,
                exchange.getRequestURI().getQuery().contains("since=0")
                        ? "organizations-page1.json" : "organizations-page2.json"));

        List<String> organizations = gitHub.organizations();

        assertAll(
                () -> assertThat(organizations).containsExactly("payments", "platform"),
                () -> assertThat(server.requests).containsExactly("GET /api/v3/organizations?per_page=100&since=0",
                        "GET /api/v3/organizations?per_page=100&since=42"),
                () -> assertThat(server.authorizations).containsOnly("Bearer t0ken"));
    }

    @Test
    void pagesRepositoriesByCursorAndKeepsArchivedAndForkFlags() {
        server.route("/api/graphql", exchange -> RecordedGitHub.respond(exchange, 200,
                server.body(exchange).contains("Y3Vyc29yOjE=")
                        ? "repositories-page2.json" : "repositories-page1.json"));

        List<Repository> repositories = gitHub.repositories("payments");

        assertAll(
                () -> assertThat(repositories).containsExactly(
                        new Repository("payments/ledger-api", "main", "1111111111111111111111111111111111111111",
                                false, false, "2026-10-30T09:12:00Z"),
                        new Repository("payments/empty", null, null, true, true, null)),
                () -> assertThat(server.graphqlBodies.getFirst()).contains("\"org\":\"payments\"")
                        .contains("\"cursor\":null"));
    }

    @Test
    void githubDotComHasRestAtTheRootAndGraphqlBesideIt() {
        GitHubHttp dotCom = new GitHubHttp(new GitHubRequests(HttpClient.newHttpClient(), server.root(),
                TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN"), new RetryPolicy(1, Duration.ofHours(1)),
                Duration.ofSeconds(10), wait -> { }), "");
        server.routeFixture("/repos/payments/ledger-api/git/trees/abc", "tree.json");

        var tree = dotCom.tree("payments/ledger-api", "abc", true);

        assertAll(
                () -> assertThat(tree).isPresent(),
                () -> assertThat(server.requests)
                        .containsExactly("GET /repos/payments/ledger-api/git/trees/abc?recursive=1"));
    }

    @Test
    void readsARecursiveTree() {
        server.routeFixture(TREE, "tree.json");

        Tree tree = gitHub.tree("payments/ledger-api", "abc", true).orElseThrow();

        assertAll(
                () -> assertThat(tree.truncated()).isFalse(),
                () -> assertThat(tree.entries()).extracting(Tree.Entry::path, Tree.Entry::mode, Tree.Entry::isBlob)
                        .containsExactly(tuple("Dockerfile", "100644", true), tuple("src", "040000", false)),
                () -> assertThat(server.requests).containsExactly("GET " + TREE + "?recursive=1"));
    }

    @Test
    void listsOnlyDirectChildrenWhenNotRecursive() {
        server.routeFixture(TREE, "tree.json");

        gitHub.tree("payments/ledger-api", "abc", false);

        assertThat(server.requests).containsExactly("GET " + TREE);
    }

    @Test
    void fetchesBlobsInOneQueryAndLeavesOutMissingOnes() {
        server.routeGraphql("blobs.json");

        List<BlobText> blobs = gitHub.blobs(List.of(
                new BlobRequest("payments/ledger-api", "d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1"),
                new BlobRequest("payments/ledger-api", "e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2")));

        assertAll(
                () -> assertThat(blobs).containsExactly(new BlobText("d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1",
                        "FROM ubi9/ubi-minimal\n", false, false)),
                () -> assertThat(server.requests).hasSize(1),
                () -> assertThat(server.graphqlBodies.getFirst())
                        .contains("repository(owner: \\\"payments\\\", name: \\\"ledger-api\\\")"));
    }

    @Test
    void makesNoCallForNoBlobs() {
        List<BlobText> blobs = gitHub.blobs(List.of());

        assertAll(
                () -> assertThat(blobs).isEmpty(),
                () -> assertThat(server.requests).isEmpty());
    }

    @Test
    void fetchesLastChangesInOneQuery() {
        server.routeGraphql("history.json");

        List<LastChange> changes = gitHub.lastChanges(List.of(GraphQlHistoryTest.LEDGER,
                GraphQlHistoryTest.NO_HISTORY, GraphQlHistoryTest.GONE));

        assertAll(
                () -> assertThat(changes).extracting(LastChange::changedAt).containsExactly("2026-03-02T10:11:12Z"),
                () -> assertThat(server.requests).hasSize(1));
    }

    @Test
    void makesNoCallForNoLastChanges() {
        List<LastChange> changes = gitHub.lastChanges(List.of());

        assertAll(
                () -> assertThat(changes).isEmpty(),
                () -> assertThat(server.requests).isEmpty());
    }

    @Test
    void organizationsPathCarriesPageSizeAndSince() {
        assertThat(gitHub.organizationsPath(42)).isEqualTo("v3/organizations?per_page=100&since=42");
    }

    @Test
    void loginsAreReadFromAnOrganizationsPage() {
        var page = new ObjectMapper().readTree(RecordedGitHub.fixture("organizations-page1.json"));

        assertThat(GitHubHttp.logins(page)).containsExactly("payments", "platform");
    }
}
