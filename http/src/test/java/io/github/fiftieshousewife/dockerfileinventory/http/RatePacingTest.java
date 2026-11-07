package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RatePacingTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void underATenthOfTheLimitLeftCallsAreSpreadOverTheTimeToReset() {
        assertThat(RatePacing.pause(headers("5000", "400", NOW.plusSeconds(800)), NOW)).contains(Duration.ofSeconds(2));
    }

    @Test
    void withMoreThanATenthLeftOrNoRateLimitHeadersThereIsNoPause() {
        assertThat(RatePacing.pause(headers("5000", "600", NOW.plusSeconds(800)), NOW)).isEmpty();
        assertThat(RatePacing.pause(HttpHeaders.of(Map.of(), (n, v) -> true), NOW)).isEmpty();
    }

    @Test
    void aPassedResetMeansNoPause() {
        assertThat(RatePacing.pause(headers("5000", "10", NOW.minusSeconds(5)), NOW)).isEmpty();
    }

    private static HttpHeaders headers(String limit, String remaining, Instant reset) {
        Map<String, String> values = Map.of("x-ratelimit-limit", limit, "x-ratelimit-remaining", remaining,
                "x-ratelimit-reset", String.valueOf(reset.getEpochSecond()));
        return HttpHeaders.of(values.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue()))), (n, v) -> true);
    }
}
