package io.github.fiftieshousewife.dockerfileinventory.http;

import java.time.Instant;

/** A rate limit resets later than {@code MAX_RATE_LIMIT_WAIT} allows: the run stops and resumes when rerun. */
public final class RateLimitWaitTooLong extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RateLimitWaitTooLong(Instant reset) {
        super("the rate limit resets at " + reset + ", later than MAX_RATE_LIMIT_WAIT allows; rerun after then");
    }
}
