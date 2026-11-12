package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final RetryPolicy POLICY = new RetryPolicy(5, Duration.ofHours(1));
    private static final Map<String, String> EXHAUSTED = Map.of("x-ratelimit-remaining", "0",
            "x-ratelimit-reset", String.valueOf(NOW.plusSeconds(120).getEpochSecond()));

    @ParameterizedTest
    @ValueSource(ints = {429, 502, 503, 504})
    void aRateLimitedGatewayOrUnavailableAnswerIsRetried(int status) {
        assertThat(POLICY.retryable(status, headers(Map.of()))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 401, 403, 404, 409, 500})
    void otherAnswersAreNotRetried(int status) {
        assertThat(POLICY.retryable(status, headers(Map.of()))).isFalse();
    }

    @Test
    void aForbiddenAnswerIsRetriedOnlyWhenItIsARateLimit() {
        assertAll(
                () -> assertThat(POLICY.retryable(403, headers(Map.of("retry-after", "7")))).isTrue(),
                () -> assertThat(POLICY.retryable(403, headers(EXHAUSTED))).isTrue(),
                () -> assertThat(POLICY.retryable(403, headers(Map.of("x-ratelimit-remaining", "0")))).isFalse());
    }

    @Test
    void anAnswerIsRateLimitedWhenItSaysHowLongToWait() {
        assertAll(
                () -> assertThat(RetryPolicy.rateLimited(headers(Map.of("retry-after", "0")))).isTrue(),
                () -> assertThat(RetryPolicy.rateLimited(headers(EXHAUSTED))).isTrue(),
                () -> assertThat(RetryPolicy.rateLimited(headers(Map.of("x-ratelimit-reset", "1")))).isFalse(),
                () -> assertThat(RetryPolicy.rateLimited(headers(Map.of()))).isFalse());
    }

    @Test
    void aGatewayAnswerWaitsTheBackoff() {
        assertThat(POLICY.delay(502, headers(Map.of()), 1, NOW))
                .isBetween(Duration.ofSeconds(1), Duration.ofMillis(1200));
    }

    @Test
    void retryAfterIsHonouredOnA429OrA403() {
        assertAll(
                () -> assertThat(POLICY.delay(429, headers(Map.of("retry-after", "30")), 1, NOW))
                        .isEqualTo(Duration.ofSeconds(30)),
                () -> assertThat(POLICY.delay(403, headers(Map.of("retry-after", "7")), 1, NOW))
                        .isEqualTo(Duration.ofSeconds(7)));
    }

    @Test
    void anExhaustedRateLimitWaitsUntilItsResetAndASecond() {
        assertThat(POLICY.delay(403, headers(EXHAUSTED), 1, NOW)).isEqualTo(Duration.ofSeconds(121));
    }

    @Test
    void a429WithoutAWaitBacksOff() {
        assertThat(POLICY.delay(429, headers(Map.of()), 1, NOW))
                .isBetween(Duration.ofSeconds(1), Duration.ofMillis(1200));
    }

    @Test
    void aRateLimitWaitLongerThanTheMaximumFailsWithTheResetTime() {
        Map<String, String> exhausted = Map.of("x-ratelimit-remaining", "0",
                "x-ratelimit-reset", String.valueOf(NOW.plusSeconds(7200).getEpochSecond()));

        assertThatThrownBy(() -> POLICY.delay(403, headers(exhausted), 1, NOW))
                .isInstanceOf(RateLimitWaitTooLong.class).hasMessageContaining("2026-11-07T14:00:01Z")
                .hasMessageContaining("MAX_RATE_LIMIT_WAIT");
    }

    @Test
    void backoffDoublesFromOneSecondUpToAMinuteWithAtMostAFifthAdded() {
        assertAll(
                () -> assertThat(POLICY.backoff(1)).isBetween(Duration.ofSeconds(1), Duration.ofMillis(1200)),
                () -> assertThat(POLICY.backoff(3)).isBetween(Duration.ofSeconds(4), Duration.ofMillis(4800)),
                () -> assertThat(POLICY.backoff(20)).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(72)));
    }

    @Test
    void aZeroFirstBackoffNeverWaits() {
        RetryPolicy prompt = new RetryPolicy(5, Duration.ofHours(1), Duration.ZERO);

        assertAll(
                () -> assertThat(prompt.backoff(1)).isZero(),
                () -> assertThat(prompt.backoff(20)).isZero());
    }

    private static HttpHeaders headers(Map<String, String> values) {
        return HttpHeaders.of(values.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> List.of(e.getValue()))), (name, value) -> true);
    }
}
