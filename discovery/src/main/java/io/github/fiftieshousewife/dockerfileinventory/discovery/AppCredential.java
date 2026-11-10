package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.function.Supplier;

// The app itself, for the calls that list its installations and issue their tokens: a JWT signed for each call.
final class AppCredential implements Credential {

    private final String appId;
    private final PrivateKey key;
    private final Supplier<Instant> now;

    AppCredential(String appId, PrivateKey key, Supplier<Instant> now) {
        this.appId = appId;
        this.key = key;
        this.now = now;
    }

    @Override
    public String token() {
        return AppJwt.of(appId, key, now.get());
    }

    @Override
    public boolean refresh() {
        return false;
    }

    @Override
    public String toString() {
        return "GitHub App " + appId;
    }
}
