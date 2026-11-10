package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

// A GitHub App installation's token lasts an hour.
final class InstallationToken implements Credential {

    static final Duration MARGIN = Duration.ofMinutes(5);

    record Issued(String token, Instant expires) {
    }

    private final Supplier<Issued> issue;
    private final Supplier<Instant> now;
    private final Object lock = new Object();
    private Issued current;

    InstallationToken(Supplier<Issued> issue, Supplier<Instant> now) {
        this.issue = issue;
        this.now = now;
    }

    @Override
    public String token() {
        synchronized (lock) {
            if (current == null || now.get().isAfter(current.expires().minus(MARGIN))) {
                current = issue.get();
            }
            return current.token();
        }
    }

    @Override
    public boolean refresh() {
        synchronized (lock) {
            current = issue.get();
            return true;
        }
    }
}
