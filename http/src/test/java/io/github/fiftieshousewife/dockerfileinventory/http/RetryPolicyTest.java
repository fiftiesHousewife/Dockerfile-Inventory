package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final RetryPolicy POLICY = new RetryPolicy(5, Duration.ofHours(1));

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    void aGatewayOrUnavailableAnswerIsRetriedWithBackoff(int status) {
        assertThat(POLICY.afterResponse(status, headers(Map.of()), 1, NOW)).hasValueSatisfying(wait ->
                assertThat(wait).isBetween(Duration.ofSeconds(1), Duration.ofMillis(1200)));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 401, 404, 409, 500})
    void otherAnswersAreNotRetried(int status) {
        assertThat(POLICY.afterResponse(status, headers(Map.of()), 1, NOW)).isEmpty();
    }

    @Test
    void retryAfterIsHonouredOnA429OrA403() {
        assertThat(POLICY.afterResponse(429, headers(Map.of("retry-after", "30")), 1, NOW))
                .contains(Duration.ofSeconds(30));
        assertThat(POLICY.afterResponse(403, headers(Map.of("retry-after", "7")), 1, NOW))
                .contains(Duration.ofSeconds(7));
    }

    @Test
    void anExhaustedRateLimitWaitsUntilItsResetAndAPlain403IsARefusal() {
        Map<String, String> exhausted = Map.of("x-ratelimit-remaining", "0",
                "x-ratelimit-reset", String.valueOf(NOW.plusSeconds(120).getEpochSecond()));

        assertThat(POLICY.afterResponse(403, headers(exhausted), 1, NOW)).contains(Duration.ofSeconds(121));
        assertThat(POLICY.afterResponse(403, headers(Map.of()), 1, NOW)).isEmpty();
    }

    @Test
    void aRateLimitWaitLongerThanTheMaximumFailsWithTheResetTime() {
        Map<String, String> exhausted = Map.of("x-ratelimit-remaining", "0",
                "x-ratelimit-reset", String.valueOf(NOW.plusSeconds(7200).getEpochSecond()));

        assertThatThrownBy(() -> POLICY.afterResponse(403, headers(exhausted), 1, NOW))
                .isInstanceOf(RateLimitWaitTooLong.class).hasMessageContaining("2026-11-07T14:00:01Z")
                .hasMessageContaining("MAX_RATE_LIMIT_WAIT");
    }

    @Test
    void networkErrorsAndTimeoutsAreRetriedUntilTheAttemptsRunOut() {
        assertThat(POLICY.afterError(new IOException("Connection reset"), 1)).isPresent();
        assertThat(POLICY.afterError(new HttpTimeoutException("request timed out"), 4)).isPresent();
        assertThat(POLICY.afterError(new IOException("Connection reset"), 5)).isEmpty();
        assertThat(POLICY.afterResponse(503, headers(Map.of()), 5, NOW)).isEmpty();
    }

    @Test
    void backoffDoublesFromOneSecondUpToAMinuteWithAtMostAFifthAdded() {
        assertThat(RetryPolicy.backoff(1)).isBetween(Duration.ofSeconds(1), Duration.ofMillis(1200));
        assertThat(RetryPolicy.backoff(3)).isBetween(Duration.ofSeconds(4), Duration.ofMillis(4800));
        assertThat(RetryPolicy.backoff(20)).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(72));
    }

    private static HttpHeaders headers(Map<String, String> values) {
        return HttpHeaders.of(values.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> List.of(e.getValue()))), (name, value) -> true);
    }
}
