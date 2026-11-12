package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProgressTest {

    private static final Instant START = Instant.parse("2026-11-12T08:00:00Z");

    @Test
    void theLineSaysThePhaseHowFarItHasGotWhatItFoundTheCallsAndTheTime() {
        AtomicReference<Instant> now = new AtomicReference<>(START);
        Progress progress = new Progress(() -> 310L, now::get);
        progress.start(Progress.Phase.SCANNING, 75);
        progress.done(40, 120);
        now.set(START.plusSeconds(150));

        assertThat(progress.line())
                .isEqualTo("discovery: scanning repositories, 40 of 75, 120 Dockerfiles found, 310 calls, 2m 30s in");
    }

    @Test
    void theTimeInIsShortest() {
        assertAll(
                () -> assertThat(Progress.elapsed(Duration.ofSeconds(9))).isEqualTo("9s"),
                () -> assertThat(Progress.elapsed(Duration.ofSeconds(150))).isEqualTo("2m 30s"),
                () -> assertThat(Progress.elapsed(Duration.ofSeconds(3723))).isEqualTo("1h 2m 3s"));
    }
}
