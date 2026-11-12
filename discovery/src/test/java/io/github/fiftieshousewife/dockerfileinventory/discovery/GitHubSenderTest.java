package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.http.RetryPolicy;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class GitHubSenderTest {

    private static final TokenSource TOKEN = TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN");
    private static final HttpRequest REQUEST = HttpRequest.newBuilder(URI.create("https://ghe.example/")).build();

    private final List<Duration> pauses = new ArrayList<>();

    private static URI closedServer() throws IOException {
        RecordedGitHub server = new RecordedGitHub();
        server.close();
        return server.root();
    }

    private GitHubSender sender(int attempts, Sleeper sleeper) {
        return new GitHubSender(HttpClient.newHttpClient(), TOKEN, new RetryPolicy(attempts, Duration.ofHours(1),
                Duration.ZERO), sleeper);
    }

    @Test
    void anUnreachableServerIsTriedAgainThenFailsNamingTheRequest() throws IOException {
        HttpRequest closed = HttpRequest.newBuilder(closedServer()).build();

        assertThatExceptionOfType(GitHubException.class)
                .isThrownBy(() -> sender(2, pauses::add).send(() -> closed))
                .withMessage("GET " + closed.uri() + " failed");
    }

    @Test
    void aGatewayErrorIsTriedAgain() throws IOException {
        AtomicLong calls = new AtomicLong();
        try (RecordedGitHub server = new RecordedGitHub()) {
            server.route("/flaky", exchange -> RecordedGitHub.respondText(exchange,
                    calls.incrementAndGet() == 1 ? 502 : 200, "{}"));
            HttpRequest flaky = HttpRequest.newBuilder(server.root().resolve("/flaky")).build();

            assertThat(sender(3, pauses::add).send(() -> flaky).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void anAnswerPausesOnlyToPaceARateLimitRunningLow() throws IOException {
        try (RecordedGitHub server = new RecordedGitHub()) {
            server.route("/plain", exchange -> RecordedGitHub.respondText(exchange, 200, "{}"));
            server.route("/low", exchange -> {
                exchange.getResponseHeaders().add("x-ratelimit-limit", "5000");
                exchange.getResponseHeaders().add("x-ratelimit-remaining", "1");
                exchange.getResponseHeaders().add("x-ratelimit-reset",
                        String.valueOf(Instant.now().plusSeconds(100).getEpochSecond()));
                RecordedGitHub.respondText(exchange, 200, "{}");
            });
            GitHubSender sender = sender(3, pauses::add);

            sender.send(() -> HttpRequest.newBuilder(server.root().resolve("/plain")).build());
            sender.send(() -> HttpRequest.newBuilder(server.root().resolve("/low")).build());
        }

        assertThat(pauses).hasSize(1);
    }

    @Test
    void aForbiddenAnswerAskingForSsoStopsTheRun() {
        CannedResponse refused = CannedResponse.of(403, "", Map.of("x-github-sso", List.of("required; url=x")));

        assertThatExceptionOfType(SsoRequired.class).isThrownBy(() -> GitHubSender.ssoRequired(refused));
    }

    @Test
    void aForbiddenAnswerWithoutTheSsoHeaderIsLeftToTheRetries() {
        assertThatCode(() -> GitHubSender.ssoRequired(CannedResponse.of(403, ""))).doesNotThrowAnyException();
    }

    @Test
    void aPauseThatIsInterruptedFailsNamingTheRequest() {
        GitHubSender interrupted = sender(1, wait -> {
            throw new InterruptedException();
        });

        assertAll(() -> assertThatExceptionOfType(GitHubException.class)
                        .isThrownBy(() -> interrupted.pause(Duration.ofSeconds(1), REQUEST))
                        .withMessage("interrupted calling https://ghe.example/"),
                () -> assertThat(Thread.interrupted()).isTrue());
    }

    @Test
    void eachCallWaitsItsTurnOnTheThrottle() throws IOException {
        AtomicLong clock = new AtomicLong();
        List<Duration> waits = new ArrayList<>();
        Throttle throttle = new Throttle(1, clock::get, wait -> {
            waits.add(wait);
            clock.addAndGet(wait.toNanos());
        });
        try (RecordedGitHub server = new RecordedGitHub()) {
            server.route("/ok", exchange -> RecordedGitHub.respondText(exchange, 200, "{}"));
            GitHubSender sender = new GitHubSender(HttpClient.newHttpClient(), TOKEN,
                    new RetryPolicy(1, Duration.ofHours(1), Duration.ZERO), pauses::add, throttle);
            HttpRequest ok = HttpRequest.newBuilder(server.root().resolve("/ok")).build();

            sender.send(() -> ok);
            sender.send(() -> ok);
        }

        assertThat(waits).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void aWaitOfHalfAMinuteOrMoreIsTold() {
        assertAll(
                () -> assertThat(GitHubSender.told(Duration.ofSeconds(29))).isFalse(),
                () -> assertThat(GitHubSender.told(Duration.ofSeconds(30))).isTrue());
    }

    @Test
    void aToldWaitSaysHowLongAndUntilWhen() {
        assertThat(GitHubSender.waiting(Duration.ofMinutes(41), Instant.parse("2026-11-12T07:34:12.5Z")))
                .isEqualTo("discovery: waiting 41m 0s for GitHub, until 2026-11-12T08:15:12Z");
    }
}
