package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressLogTest {

    private final Progress progress = new Progress(() -> 0L, () -> Instant.EPOCH);

    @Test
    void eachTickLogsTheProgressLine() {
        List<String> lines = new ArrayList<>();
        try (ProgressLog log = new ProgressLog(progress, Duration.ofHours(1), lines::add)) {
            log.tick();
        }

        assertThat(lines).containsExactly(progress.line());
    }

    @Test
    void nothingIsLoggedWhileTheRunAsksWhetherToGoOn() {
        List<String> lines = new ArrayList<>();
        progress.start(Progress.Phase.ASKING, 0);
        try (ProgressLog log = new ProgressLog(progress, Duration.ofHours(1), lines::add)) {
            log.tick();
        }

        assertThat(lines).isEmpty();
    }

    @Test
    void closingStopsTheSchedule() {
        ProgressLog log = new ProgressLog(progress, Duration.ofHours(1), line -> { });
        log.close();

        assertThat(log.stopped()).isTrue();
    }
}
