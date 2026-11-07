package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

final class RateLimit {

    private static final Duration MARGIN = Duration.ofSeconds(1);

    private RateLimit() {
    }

    // GitHub signals its secondary limits with Retry-After, and its primary limit with X-RateLimit-Remaining: 0 and
    // a reset time. Any other 403 is a real refusal and is not retried.
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
            return Optional.of(untilReset(now, reset.getAsLong()));
        }
        return Optional.empty();
    }

    static Duration untilReset(Instant now, long resetEpochSecond) {
        Duration untilReset = Duration.between(now, Instant.ofEpochSecond(resetEpochSecond));
        return (untilReset.isNegative() ? Duration.ZERO : untilReset).plus(MARGIN);
    }
}
