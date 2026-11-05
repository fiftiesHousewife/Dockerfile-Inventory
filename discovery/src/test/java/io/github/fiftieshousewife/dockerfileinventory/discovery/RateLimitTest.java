package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimitTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_000_000);

    @Test
    void waitsForRetryAfterOnASecondaryLimit() {
        assertThat(RateLimit.waitBeforeRetry(403, headers(Map.of("retry-after", "30")), NOW))
                .contains(Duration.ofSeconds(30));
    }

    @Test
    void waitsUntilTheResetWhenThePrimaryLimitIsSpent() {
        var headers = headers(Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "1000120"));

        assertThat(RateLimit.waitBeforeRetry(403, headers, NOW)).contains(Duration.ofSeconds(121));
    }

    @Test
    void waitsOnlyTheMarginWhenTheResetHasPassed() {
        var headers = headers(Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "999000"));

        assertThat(RateLimit.waitBeforeRetry(429, headers, NOW)).contains(Duration.ofSeconds(1));
    }

    @Test
    void doesNotRetryAPlainRefusal() {
        assertThat(RateLimit.waitBeforeRetry(403, headers(Map.of("x-ratelimit-remaining", "4999")), NOW)).isEmpty();
    }

    @Test
    void doesNotRetryOtherStatuses() {
        assertThat(RateLimit.waitBeforeRetry(500, headers(Map.of("retry-after", "30")), NOW)).isEmpty();
    }

    private static HttpHeaders headers(Map<String, String> values) {
        Map<String, List<String>> lists = new java.util.HashMap<>();
        values.forEach((k, v) -> lists.put(k, List.of(v)));
        return HttpHeaders.of(lists, (k, v) -> true);
    }
}
