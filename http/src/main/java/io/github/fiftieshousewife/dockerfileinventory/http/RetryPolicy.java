package io.github.fiftieshousewife.dockerfileinventory.http;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * When to call again: after a network error or a gateway's 502, 503 or 504, with exponential backoff from
 * {@code firstBackoff}; after a rate limit, when the server says, up to {@code maxRateLimitWait}. A 403 without
 * rate-limit headers is a refusal.
 */
public record RetryPolicy(int attempts, Duration maxRateLimitWait, Duration firstBackoff) {

    private static final Set<Integer> TRANSIENT = Set.of(502, 503, 504);
    private static final Duration MARGIN = Duration.ofSeconds(1);
    private static final Duration LONGEST_BACKOFF = Duration.ofSeconds(60);

    /**
     * A policy whose backoff starts at a second.
     *
     * @param attempts the most calls for one request
     * @param maxRateLimitWait the longest wait for a rate limit before the run stops
     */
    public RetryPolicy(int attempts, Duration maxRateLimitWait) {
        this(attempts, maxRateLimitWait, Duration.ofSeconds(1));
    }

    /**
     * Whether an answer is worth another try.
     *
     * @param status the answer's status
     * @param headers the answer's headers
     * @return true for a rate limit, a gateway's error or an unavailable server
     */
    public boolean retryable(int status, HttpHeaders headers) {
        return status == 429 || TRANSIENT.contains(status)
                || status == 403 && rateLimited(headers);
    }

    /**
     * How long to wait before trying an answer again.
     *
     * @param status the answer's status
     * @param headers the answer's headers
     * @param attempt the attempt that answered, from 1
     * @param now the time
     * @return the server's wait for a rate limit, the backoff otherwise
     * @throws RateLimitWaitTooLong when the server's wait is beyond {@code maxRateLimitWait}
     */
    public Duration delay(int status, HttpHeaders headers, int attempt, Instant now) {
        return (status == 429 || status == 403 ? rateLimitWait(headers, now) : Optional.<Duration>empty())
                .orElseGet(() -> backoff(attempt));
    }

    static boolean rateLimited(HttpHeaders headers) {
        return headers.firstValue("retry-after").isPresent()
                || headers.firstValue("x-ratelimit-remaining").filter("0"::equals).isPresent()
                && headers.firstValue("x-ratelimit-reset").isPresent();
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

    /**
     * How long to wait after a network error or an answer with no wait of its own.
     *
     * @param attempt the attempt that failed, from 1
     * @return {@code firstBackoff} doubled each attempt up to a minute, with up to a fifth added
     */
    public Duration backoff(int attempt) {
        long millis = Math.min(LONGEST_BACKOFF.toMillis(), firstBackoff.toMillis() << Math.min(attempt - 1, 20));
        return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis / 5 + 1));
    }
}
