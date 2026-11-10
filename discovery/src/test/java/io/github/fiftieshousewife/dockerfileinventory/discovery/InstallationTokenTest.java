package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class InstallationTokenTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-10T12:00:00Z"));
    private final AtomicInteger issued = new AtomicInteger();
    private final InstallationToken token = new InstallationToken(() -> new InstallationToken.Issued(
            "ghs_" + issued.incrementAndGet(), now.get().plus(Duration.ofHours(1))), now::get);

    @Test
    void aTokenIsIssuedOnceAndKeptWhileItHasTimeLeft() {
        token.token();
        now.set(now.get().plus(Duration.ofMinutes(50)));

        assertAll(
                () -> assertThat(token.token()).isEqualTo("ghs_1"),
                () -> assertThat(issued.get()).isEqualTo(1));
    }

    @Test
    void aTokenWithinFiveMinutesOfExpiringIsRenewed() {
        token.token();
        now.set(now.get().plus(Duration.ofMinutes(56)));

        assertThat(token.token()).isEqualTo("ghs_2");
    }

    @Test
    void aRefusedTokenIsRenewedAtOnceAndWorthAnotherTry() {
        token.token();

        assertAll(
                () -> assertThat(token.refresh()).isTrue(),
                () -> assertThat(token.token()).isEqualTo("ghs_2"));
    }

    @Test
    void theAppsJwtIsSignedAfreshForEachCallAndNeverRefreshed() {
        AppCredential app = new AppCredential("1234", AppJwtTest.KEYS.getPrivate(), now::get);

        assertAll(
                () -> assertThat(app.token().split("\\.")).hasSize(3),
                () -> assertThat(app.refresh()).isFalse());
    }
}
