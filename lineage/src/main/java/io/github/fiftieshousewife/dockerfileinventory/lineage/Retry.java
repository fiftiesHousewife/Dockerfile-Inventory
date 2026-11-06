package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Optional;

/**
 * Decides whether a response means "try again later" and how long to wait. Artifactory answers 429 when a rate
 * limit is hit and 503 while it is overloaded or restarting, sometimes with {@code Retry-After}. Anything else,
 * including 403, is a real answer and is not retried.
 */
final class Retry {

    static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);

    private Retry() {
    }

    static Optional<Duration> waitBeforeRetry(int status, HttpHeaders headers) {
        if (status != 429 && status != 503) {
            return Optional.empty();
        }
        var retryAfter = headers.firstValueAsLong("Retry-After");
        return Optional.of(retryAfter.isPresent() ? Duration.ofSeconds(retryAfter.getAsLong()) : DEFAULT_WAIT);
    }
}
