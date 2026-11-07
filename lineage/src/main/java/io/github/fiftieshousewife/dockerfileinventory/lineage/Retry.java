package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Optional;

final class Retry {

    static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);

    private Retry() {
    }

    // Artifactory answers 429 at a rate limit and 503 while overloaded or restarting; any other status, 403
    // included, is a real answer.
    static Optional<Duration> waitBeforeRetry(int status, HttpHeaders headers) {
        if (status != 429 && status != 503) {
            return Optional.empty();
        }
        var retryAfter = headers.firstValueAsLong("Retry-After");
        return Optional.of(retryAfter.isPresent() ? Duration.ofSeconds(retryAfter.getAsLong()) : DEFAULT_WAIT);
    }
}
