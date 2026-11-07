package io.github.fiftieshousewife.dockerfileinventory.http;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * When to call again: after a network error or a gateway's 502, 503 or 504, with exponential backoff; after a rate
 * limit, when the server says, up to {@code maxRateLimitWait}. A 403 without rate-limit headers is a refusal.
 */
public record RetryPolicy(int attempts, Duration maxRateLimitWait) {

    private static final Set<Integer> TRANSIENT = Set.of(502, 503, 504);
    private static final Duration MARGIN = Duration.ofSeconds(1);
    private static final Duration LONGEST_BACKOFF = Duration.ofSeconds(60);

    public Optional<Duration> afterResponse(int status, HttpHeaders headers, int attempt, Instant now) {
        if (attempt >= attempts) {
            return Optional.empty();
        }
        Optional<Duration> rateLimited = status == 429 || status == 403 ? rateLimitWait(headers, now) : Optional.empty();
        if (rateLimited.isPresent()) {
            return rateLimited;
        }
        return status == 429 || TRANSIENT.contains(status) ? Optional.of(backoff(attempt)) : Optional.empty();
    }

    public Optional<Duration> afterError(IOException error, int attempt) {
        return attempt >= attempts ? Optional.empty() : Optional.of(backoff(attempt));
    }

    Optional<Duration> rateLimitWait(HttpHeaders headers, Instant now) {
        var retryAfter = headers.firstValueAsLong("retry-after");
        if (retryAfter.isPresent()) {
            return Optional.of(capped(Duration.ofSeconds(retryAfter.getAsLong()), now));
        }
        boolean exhausted = headers.firstValue("x-ratelimit-remaining").filter("0"::equals).isPresent();
        var reset = headers.firstValueAsLong("x-ratelimit-reset");
        if (!exhausted || reset.isEmpty()) {
            return Optional.empty();
        }
        Duration untilReset = Duration.between(now, Instant.ofEpochSecond(reset.getAsLong()));
        return Optional.of(capped((untilReset.isNegative() ? Duration.ZERO : untilReset).plus(MARGIN), now));
    }

    Duration capped(Duration wait, Instant now) {
        if (wait.compareTo(maxRateLimitWait) > 0) {
            throw new RateLimitWaitTooLong(now.plus(wait));
        }
        return wait;
    }

    static Duration backoff(int attempt) {
        long seconds = Math.min(LONGEST_BACKOFF.toSeconds(), 1L << Math.min(attempt - 1, 20));
        long jitter = ThreadLocalRandom.current().nextLong(seconds * 200 + 1);
        return Duration.ofSeconds(seconds).plusMillis(jitter);
    }
}
