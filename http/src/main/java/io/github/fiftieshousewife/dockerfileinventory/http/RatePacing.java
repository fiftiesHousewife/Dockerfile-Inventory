package io.github.fiftieshousewife.dockerfileinventory.http;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Slows down before a rate limit runs out rather than after: with under a tenth of the limit left, the calls left are
 * spread evenly over the time until it resets, so a long run neither stalls for an hour nor trips secondary limits.
 */
public final class RatePacing {

    private static final int SHARE = 10;

    private RatePacing() {
    }

    public static Optional<Duration> pause(HttpHeaders headers, Instant now) {
        var limit = headers.firstValueAsLong("x-ratelimit-limit");
        var remaining = headers.firstValueAsLong("x-ratelimit-remaining");
        var reset = headers.firstValueAsLong("x-ratelimit-reset");
        if (limit.isEmpty() || remaining.isEmpty() || reset.isEmpty()
                || remaining.getAsLong() * SHARE >= limit.getAsLong()) {
            return Optional.empty();
        }
        Duration untilReset = Duration.between(now, Instant.ofEpochSecond(reset.getAsLong()));
        return untilReset.isNegative() || untilReset.isZero() ? Optional.empty()
                : Optional.of(untilReset.dividedBy(Math.max(remaining.getAsLong(), 1)));
    }
}
