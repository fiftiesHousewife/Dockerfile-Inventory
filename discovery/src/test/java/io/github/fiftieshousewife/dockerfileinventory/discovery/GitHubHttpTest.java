package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitHubHttpTest {

    private HttpServer server;
    private GitHubHttp gitHub;
    private final List<String> requests = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    private final List<String> graphqlBodies = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        gitHub = new GitHubHttp(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"), "t0ken");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void pagesOrganizationsBySinceUntilAnEmptyPage() {
        route("/api/v3/organizations", exchange -> respond(exchange, 200,
                exchange.getRequestURI().getQuery().contains("since=0") ? "organizations-page1.json" : "organizations-page2.json"));

        assertThat(gitHub.organizations()).containsExactly("payments", "platform");
        assertThat(requests).containsExactly(
                "GET /api/v3/organizations?per_page=100&since=0", "GET /api/v3/organizations?per_page=100&since=42");
        assertThat(authorizations).containsOnly("Bearer t0ken");
    }

    @Test
    void pagesRepositoriesByCursorAndKeepsArchivedAndForkFlags() {
        route("/api/graphql", exchange -> {
            String body = body(exchange);
            respond(exchange, 200, body.contains("Y3Vyc29yOjE=") ? "repositories-page2.json" : "repositories-page1.json");
        });

        List<Repository> repositories = gitHub.repositories("payments");

        assertThat(repositories).containsExactly(
                new Repository("payments/ledger-api", "main", "1111111111111111111111111111111111111111",
                        false, false, "2026-10-30T09:12:00Z"),
                new Repository("payments/empty", null, null, true, true, null));
        assertThat(graphqlBodies.getFirst()).contains("\"org\":\"payments\"").contains("\"cursor\":null");
    }

    @Test
    void readsARecursiveTree() {
        route("/api/v3/repos/payments/ledger-api/git/trees/abc", exchange -> respond(exchange, 200, "tree.json"));

        Tree tree = gitHub.tree("payments/ledger-api", "abc", true).orElseThrow();

        assertThat(tree.truncated()).isFalse();
        assertThat(tree.entries()).extracting(Tree.Entry::path, Tree.Entry::isBlob)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Dockerfile", true),
                        org.assertj.core.groups.Tuple.tuple("src", false));
        assertThat(requests).containsExactly("GET /api/v3/repos/payments/ledger-api/git/trees/abc?recursive=1");
    }

    @Test
    void listsOnlyDirectChildrenWhenNotRecursive() {
        route("/api/v3/repos/payments/ledger-api/git/trees/abc", exchange -> respond(exchange, 200, "tree.json"));

        gitHub.tree("payments/ledger-api", "abc", false);

        assertThat(requests).containsExactly("GET /api/v3/repos/payments/ledger-api/git/trees/abc");
    }

    @Test
    void treatsAnEmptyRepositoryAsNoTree() {
        route("/api/v3/repos/payments/empty/git/trees/abc", exchange -> respondText(exchange, 409, "{\"message\":\"Git Repository is empty.\"}"));

        assertThat(gitHub.tree("payments/empty", "abc", true)).isEmpty();
    }

    @Test
    void fetchesBlobsInOneQueryAndLeavesOutMissingOnes() {
        route("/api/graphql", exchange -> {
            body(exchange);
            respond(exchange, 200, "blobs.json");
        });

        List<BlobText> blobs = gitHub.blobs(List.of(
                new BlobRequest("payments/ledger-api", "d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1"),
                new BlobRequest("payments/ledger-api", "e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2e2")));

        assertThat(blobs).containsExactly(new BlobText("d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1",
                "FROM ubi9/ubi-minimal\n", false, false));
        assertThat(requests).hasSize(1);
        assertThat(graphqlBodies.getFirst()).contains("repository(owner: \\\"payments\\\", name: \\\"ledger-api\\\")");
    }

    @Test
    void makesNoCallForNoBlobs() {
        assertThat(gitHub.blobs(List.of())).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void failsOnGraphqlErrors() {
        route("/api/graphql", exchange -> {
            body(exchange);
            respond(exchange, 200, "graphql-error.json");
        });

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> gitHub.repositories("gone"))
                .withMessageContaining("Could not resolve to an Organization");
    }

    @Test
    void retriesAfterASecondaryRateLimit() {
        AtomicInteger calls = new AtomicInteger();
        route("/api/v3/repos/payments/ledger-api/git/trees/abc", exchange -> {
            if (calls.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                respondText(exchange, 403, "{\"message\":\"You have exceeded a secondary rate limit.\"}");
            } else {
                respond(exchange, 200, "tree.json");
            }
        });

        assertThat(gitHub.tree("payments/ledger-api", "abc", true)).isPresent();
        assertThat(calls).hasValue(2);
    }

    @Test
    void failsOnAServerError() {
        route("/api/v3/repos/payments/ledger-api/git/trees/abc", exchange -> respondText(exchange, 502, "Bad Gateway"));

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> gitHub.tree("payments/ledger-api", "abc", true))
                .withMessageContaining("returned 502");
    }

    @Test
    void writesGraphqlStringLiteralsEscaped() {
        assertThat(GitHubHttp.blobQuery(List.of(new BlobRequest("pay/a\"b", "f00d"))))
                .contains("bf00d: repository(owner: \"pay\", name: \"a\\\"b\")");
    }

    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private void route(String path, Handler handler) {
        server.createContext(path, exchange -> {
            synchronized (requests) {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
                authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            }
            handler.handle(exchange);
        });
    }

    private String body(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        graphqlBodies.add(body);
        return body;
    }

    private static void respond(HttpExchange exchange, int status, String fixture) throws IOException {
        respondText(exchange, status, fixture(fixture));
    }

    private static void respondText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String fixture(String name) {
        try (InputStream in = GitHubHttpTest.class.getResourceAsStream("/fixtures/ghe/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
