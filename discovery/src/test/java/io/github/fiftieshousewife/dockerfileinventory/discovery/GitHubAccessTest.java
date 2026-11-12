package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubAccessTest {

    private static final ApiChoice AUTO = new ApiChoice(false, false);
    private static final GitHubApi API = GitHubApi.from("https://github.com/acme");

    private static OrganisationCredentials credentials(Map<String, String> env) {
        return GitHubAccess.credentials(env, API, HttpSettings.from(Map.of()), Throttle.NONE);
    }

    @Test
    void aTokenAloneReadsEveryOrganisation() {
        assertThat(credentials(Map.of("GHE_TOKEN", "t")).of("acme").token()).isEqualTo("t");
    }

    @Test
    void neitherATokenNorAnAppIsRefused() {
        assertThatThrownBy(() -> credentials(Map.of())).hasMessage("set GHE_TOKEN or GHE_TOKEN_FILE, or GHE_APP_ID "
                + "with GHE_APP_PRIVATE_KEY_FILE");
    }

    @Test
    void anAppWithoutItsKeyIsRefusedByName() {
        assertThatThrownBy(() -> credentials(Map.of("GHE_APP_ID", "1234")))
                .hasMessage("GHE_APP_ID is set without GHE_APP_PRIVATE_KEY_FILE, the app's private key");
    }

    @Test
    void anAppsKeyIsReadFromItsFile(@TempDir Path dir) throws IOException {
        Path key = Files.writeString(dir.resolve("app.pem"), AppJwtTest.pem("RSA PRIVATE KEY", AppJwtTest.pkcs1()));

        assertThat(GitHubAccess.app(Map.of("GHE_APP_ID", "1234", "GHE_APP_PRIVATE_KEY_FILE", key.toString()), API,
                HttpSettings.from(Map.of()), Throttle.NONE)).isPresent();
    }

    @Test
    void eachOrganisationsCredentialIsRecordedOneALine() {
        Map<String, String> chosen = new LinkedHashMap<>();
        chosen.put("pay", "the GitHub App, installation 7");
        chosen.put("web", "GHE_TOKEN_FILE /secrets/t");

        assertAll(
                () -> assertThat(GitHubAccess.lines(chosen)).isEqualTo(
                        "{\"organization\":\"pay\",\"credential\":\"the GitHub App, installation 7\"}\n"
                                + "{\"organization\":\"web\",\"credential\":\"GHE_TOKEN_FILE /secrets/t\"}\n"),
                () -> assertThat(GitHubAccess.lines(Map.of())).isEmpty());
    }

    @Test
    void aSettingIsPresentUnlessMissingOrBlank() {
        assertAll(
                () -> assertThat(GitHubAccess.present(Map.of("GHE_APP_ID", "1"), "GHE_APP_ID")).isTrue(),
                () -> assertThat(GitHubAccess.present(Map.of("GHE_APP_ID", " "), "GHE_APP_ID")).isFalse(),
                () -> assertThat(GitHubAccess.present(Map.of(), "GHE_APP_ID")).isFalse());
    }

    @Test
    void withATokenTheOrganisationsAreTheOnesItLists() throws IOException {
        try (RecordedGitHub server = new RecordedGitHub()) {
            server.route("/api/v3/organizations", exchange -> RecordedGitHub.respond(exchange, 200,
                    exchange.getRequestURI().getQuery().contains("since=0")
                            ? "organizations-page1.json" : "organizations-page2.json"));
            server.routeGraphql("graphql-rate-limit.json");
            GitHubApi api = GitHubApi.from(server.root() + "api/");
            Map<String, String> env = Map.of("GHE_TOKEN", "t");

            GitHub client = GitHubAccess.client(api, HttpSettings.from(Map.of()), Throttle.NONE,
                    GitHubAccess.credentials(env, api, HttpSettings.from(Map.of()), Throttle.NONE), AUTO);

            assertThat(client.organizations()).containsExactly("payments", "platform");
        }
    }

    @Test
    void withAnAppAloneTheOrganisationsAreItsInstallations() {
        OrganisationCredentials.Installations app = new OrganisationCredentials.Installations() {
            @Override
            public Map<String, Long> list() {
                return Map.of("web", 9L, "pay", 7L);
            }

            @Override
            public InstallationToken.Issued issue(long installation) {
                return new InstallationToken.Issued("ghs_" + installation, Instant.MAX);
            }
        };

        GitHub client = GitHubAccess.client(API, HttpSettings.from(Map.of()), Throttle.NONE,
                new OrganisationCredentials(Optional.of(app), Optional.empty(), Instant::now), AUTO);

        assertThat(client.organizations()).containsExactly("pay", "web");
    }

    @Test
    void aCredentialGraphqlAnswersReadsOverGraphql() throws IOException {
        try (RecordedGitHub server = new RecordedGitHub()) {
            server.routeGraphql("graphql-rate-limit.json");

            assertThat(GitHubAccess.chosen(requests(server), "api/v3/", AUTO, "a token"))
                    .isInstanceOf(GitHubHttp.class);
        }
    }

    @Test
    void aCredentialGraphqlRefusesReadsOverRest() throws IOException {
        try (RecordedGitHub server = new RecordedGitHub()) {
            server.route("/api/graphql", exchange -> RecordedGitHub.respond(exchange, 403, "graphql-forbidden.json"));

            assertThat(GitHubAccess.chosen(requests(server), "api/v3/", AUTO, "a token"))
                    .isInstanceOf(GitHubRest.class);
        }
    }

    @Test
    void restChosenFromTheCommandLineAsksGraphqlNothing() throws IOException {
        try (RecordedGitHub server = new RecordedGitHub()) {
            server.routeGraphql("graphql-rate-limit.json");

            GitHub chosen = GitHubAccess.chosen(requests(server), "api/v3/", new ApiChoice(true, false), "a token");

            assertAll(
                    () -> assertThat(chosen).isInstanceOf(GitHubRest.class),
                    () -> assertThat(server.requests).isEmpty());
        }
    }

    private static GitHubRequests requests(RecordedGitHub server) {
        return new GitHubRequests(HttpClient.newHttpClient(), URI.create(server.root() + "api"),
                TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN"),
                new RetryPolicy(1, Duration.ofHours(1), Duration.ZERO),
                Duration.ofSeconds(5), wait -> { });
    }
}
