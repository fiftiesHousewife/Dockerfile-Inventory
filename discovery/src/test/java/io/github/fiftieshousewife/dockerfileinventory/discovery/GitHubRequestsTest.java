package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class GitHubRequestsTest {

    private static final String TREE = "v3/repos/payments/ledger-api/git/trees/abc";

    private RecordedGitHub server;
    private GitHubRequests requests;

    @BeforeEach
    void start() throws IOException {
        server = new RecordedGitHub();
        requests = requests(1, TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN"));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private GitHubRequests requests(int attempts, TokenSource token) {
        return new GitHubRequests(HttpClient.newHttpClient(), URI.create(server.root() + "api"), token,
                new RetryPolicy(attempts, Duration.ofHours(1)), Duration.ofSeconds(10), wait -> { });
    }

    @Test
    void aGatewayErrorIsRetriedAndThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        server.route("/api/" + TREE, exchange -> {
            if (calls.incrementAndGet() == 1) {
                RecordedGitHub.respondText(exchange, 502, "Bad Gateway");
            } else {
                RecordedGitHub.respond(exchange, 200, "tree.json");
            }
        });

        assertThat(requests(3, TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN")).rest(TREE)).isPresent();
        assertThat(calls).hasValue(2);
    }

    @Test
    void anUnreachableServerIsTriedAgainUntilTheAttemptsRunOut() {
        server.close();

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> requests(3, TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN")).rest(TREE))
                .withMessageContaining("failed");
    }

    @Test
    void a401ReadsTheTokenFileAgainAndTriesOnceMore(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("token"), "old");
        TokenSource token = TokenSource.from(Map.of("GHE_TOKEN_FILE", file.toString()), "GHE_TOKEN");
        token.token();
        FileTime unchanged = Files.getLastModifiedTime(file);
        Files.writeString(file, "new");
        Files.setLastModifiedTime(file, unchanged);
        server.route("/api/" + TREE, exchange -> {
            if ("Bearer new".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                RecordedGitHub.respond(exchange, 200, "tree.json");
            } else {
                RecordedGitHub.respondText(exchange, 401, "{\"message\":\"Bad credentials\"}");
            }
        });

        assertThat(requests(1, token).rest(TREE)).isPresent();
        assertThat(server.authorizations).containsExactly("Bearer old", "Bearer new");
    }

    @Test
    void withLittleOfTheRateLimitLeftEachCallPausesToSpreadTheRestOverTheTimeToReset() {
        List<Duration> pauses = new ArrayList<>();
        GitHubRequests paced = new GitHubRequests(HttpClient.newHttpClient(), URI.create(server.root() + "api"),
                TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN"), new RetryPolicy(1, Duration.ofHours(1)),
                Duration.ofSeconds(10), pauses::add);
        server.route("/api/" + TREE, exchange -> {
            exchange.getResponseHeaders().add("X-RateLimit-Limit", "5000");
            exchange.getResponseHeaders().add("X-RateLimit-Remaining", "100");
            exchange.getResponseHeaders().add("X-RateLimit-Reset",
                    String.valueOf(Instant.now().plusSeconds(1000).getEpochSecond()));
            RecordedGitHub.respond(exchange, 200, "tree.json");
        });

        assertThat(paced.rest(TREE)).isPresent();
        assertThat(pauses).singleElement().satisfies(pause -> assertThat(pause).isBetween(Duration.ofSeconds(9),
                Duration.ofSeconds(10)));
    }

    @Test
    void anSsoRefusalStopsTheRunNamingWhatToAuthorise() {
        server.route("/api/" + TREE, exchange -> {
            exchange.getResponseHeaders().add("X-GitHub-SSO", "required; url=https://github.com/orgs/payments/sso");
            RecordedGitHub.respondText(exchange, 403, "{\"message\":\"Resource protected by organization SAML\"}");
        });

        assertThatExceptionOfType(SsoRequired.class).isThrownBy(() -> requests.rest(TREE))
                .withMessageContaining("https://github.com/orgs/payments/sso");
    }

    @Test
    void sendsTheTokenAsABearerHeader() {
        server.routeFixture("/api/" + TREE, "tree.json");

        requests.rest(TREE);

        assertThat(server.authorizations).containsExactly("Bearer t0ken");
    }

    @Test
    void treatsAnEmptyRepositoryAsNoTree() {
        server.route("/api/" + TREE, exchange -> RecordedGitHub.respondText(exchange, 409,
                "{\"message\":\"Git Repository is empty.\"}"));

        assertThat(requests.rest(TREE)).isEmpty();
    }

    @Test
    void failsOnGraphqlErrors() {
        server.routeGraphql("graphql-error.json");

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> requests.graphql("query { x }", requests.object()))
                .withMessageContaining("Could not resolve to an Organization");
    }

    @Test
    void failsOnAGraphqlServerError() {
        server.route("/api/graphql", exchange -> RecordedGitHub.respondText(exchange, 502, "Bad Gateway"));

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> requests.graphql("query { x }", requests.object()))
                .withMessageContaining("returned 502");
    }

    @Test
    void retriesAfterASecondaryRateLimit() {
        AtomicInteger calls = new AtomicInteger();
        server.route("/api/" + TREE, exchange -> {
            if (calls.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                RecordedGitHub.respondText(exchange, 403,
                        "{\"message\":\"You have exceeded a secondary rate limit.\"}");
            } else {
                RecordedGitHub.respond(exchange, 200, "tree.json");
            }
        });

        assertThat(requests(2, TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN")).rest(TREE)).isPresent();
        assertThat(calls).hasValue(2);
    }

    @Test
    void failsOnAServerError() {
        server.route("/api/" + TREE, exchange -> RecordedGitHub.respondText(exchange, 502, "Bad Gateway"));

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> requests.rest(TREE))
                .withMessageContaining("returned 502");
    }

    @Test
    void failsAsAGitHubErrorOnUnreadableJson() {
        server.route("/api/" + TREE, exchange -> RecordedGitHub.respondText(exchange, 200,
                "<html>proxy login</html>"));

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> requests.rest(TREE))
                .withMessageContaining("unreadable JSON");
    }

    @Test
    void failsAsAGitHubErrorWhenTheServerCannotBeReached() {
        server.close();

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> requests.rest(TREE))
                .withMessageContaining("failed");
    }

    @Test
    void addsOneTrailingSlashToTheApiBase() {
        assertThat(GitHubRequests.withTrailingSlash(URI.create("https://ghe.example/api//")))
                .hasToString("https://ghe.example/api/");
    }

    @Test
    void graphqlErrorsAreAPresentNonEmptyErrorsArray() {
        ObjectMapper json = new ObjectMapper();

        assertThat(GitHubRequests.hasErrors(json.readTree("{\"errors\":[{\"message\":\"no\"}]}"))).isTrue();
        assertThat(GitHubRequests.hasErrors(json.readTree("{\"errors\":[],\"data\":{}}"))).isFalse();
        assertThat(GitHubRequests.hasErrors(json.readTree("{\"data\":{}}"))).isFalse();
    }

    @Test
    void cutsALongErrorBodyTo300Characters() {
        GitHubException failure = GitHubRequests.failure(response(502, "x".repeat(400)));

        assertThat(failure.getMessage())
                .isEqualTo("GET https://ghe.example/ returned 502: " + "x".repeat(300));
    }

    private static HttpResponse<String> response(int status, String body) {
        URI uri = URI.create("https://ghe.example/");
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (k, v) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
