package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Decides whether a response means "slow down" and for how long. GitHub signals its secondary limits with
 * {@code Retry-After}, and its primary limit with {@code X-RateLimit-Remaining: 0} plus a reset time.
 * Any other 403 is a real refusal and is not retried.
 */
final class RateLimit {

    private static final Duration MARGIN = Duration.ofSeconds(1);

    private RateLimit() {
    }

    static Optional<Duration> waitBeforeRetry(int status, HttpHeaders headers, Instant now) {
        if (status != 403 && status != 429) {
            return Optional.empty();
        }
        var retryAfter = headers.firstValueAsLong("retry-after");
        if (retryAfter.isPresent()) {
            return Optional.of(Duration.ofSeconds(retryAfter.getAsLong()));
        }
        boolean exhausted = headers.firstValue("x-ratelimit-remaining").filter("0"::equals).isPresent();
        var reset = headers.firstValueAsLong("x-ratelimit-reset");
        if (exhausted && reset.isPresent()) {
            Duration untilReset = Duration.between(now, Instant.ofEpochSecond(reset.getAsLong()));
            return Optional.of((untilReset.isNegative() ? Duration.ZERO : untilReset).plus(MARGIN));
        }
        return Optional.empty();
    }
}
