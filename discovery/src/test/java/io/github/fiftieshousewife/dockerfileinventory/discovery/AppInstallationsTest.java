package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppInstallationsTest {

    private static AppInstallations installations(RecordedGitHub server) {
        return new AppInstallations(new GitHubRequests(HttpClient.newHttpClient(), server.root(),
                new AppCredential("1234", AppJwtTest.KEYS.getPrivate(), Instant::now),
                new RetryPolicy(1, Duration.ofHours(1)), Duration.ofSeconds(10), wait -> { }), "");
    }

    @Test
    void theAppsInstallationsAreListedPageByPageByOrganisation() throws IOException {
        try (RecordedGitHub server = new RecordedGitHub()) {
            server.route("/app/installations", exchange -> RecordedGitHub.respondText(exchange, 200,
                    exchange.getRequestURI().getQuery().endsWith("&page=1")
                            ? "[{\"id\":7,\"account\":{\"login\":\"Payments\"}},"
                            + "{\"id\":9,\"account\":{\"login\":\"web\"}}]"
                            : "[]"));

            assertAll(
                    () -> assertThat(installations(server).list()).isEqualTo(Map.of("payments", 7L, "web", 9L)),
                    () -> assertThat(server.authorizations).allMatch(header -> header.startsWith("Bearer ey")));
        }
    }

    @Test
    void anInstallationsTokenIsIssuedWithItsExpiry() throws IOException {
        try (RecordedGitHub server = new RecordedGitHub()) {
            server.route("/app/installations/7/access_tokens", exchange -> RecordedGitHub.respondText(exchange, 201,
                    "{\"token\":\"ghs_abc\",\"expires_at\":\"2026-11-10T13:00:00Z\"}"));

            assertAll(
                    () -> assertThat(installations(server).issue(7)).isEqualTo(new InstallationToken.Issued("ghs_abc",
                            Instant.parse("2026-11-10T13:00:00Z"))),
                    () -> assertThat(server.requests).containsExactly("POST /app/installations/7/access_tokens"));
        }
    }
}
