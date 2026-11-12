package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SlowdownTest {

    private long now;

    private void later(Duration by) {
        now += by.toNanos();
    }

    private void calls(Slowdown slowdown, int count, long millis) {
        IntStream.range(0, count).forEach(i -> slowdown.record(now, Duration.ofMillis(millis), 200));
    }

    @Test
    void aMinuteTwiceAsSlowAsTheRunsFirstCallsHalvesTheRate() {
        Slowdown slowdown = new Slowdown(10);
        calls(slowdown, 20, 100);
        later(Duration.ofMinutes(2));

        calls(slowdown, 5, 250);

        assertThat(slowdown.rate()).isEqualTo(5);
    }

    @Test
    void aRateLimitedOrUnavailableAnswerHalvesTheRate() {
        Slowdown limited = new Slowdown(10);
        Slowdown unavailable = new Slowdown(10);

        limited.record(now, Duration.ofMillis(10), 429);
        unavailable.record(now, Duration.ofMillis(10), 503);

        assertAll(
                () -> assertThat(limited.rate()).isEqualTo(5),
                () -> assertThat(unavailable.rate()).isEqualTo(5));
    }

    @Test
    void theRateChangesAtMostOnceInTenSeconds() {
        Slowdown slowdown = new Slowdown(10);

        slowdown.record(now, Duration.ofMillis(10), 429);
        later(Duration.ofSeconds(5));
        slowdown.record(now, Duration.ofMillis(10), 429);

        assertThat(slowdown.rate()).isEqualTo(5);
    }

    @Test
    void theRateNeverFallsBelowASixteenthOfTheSetting() {
        Slowdown slowdown = new Slowdown(16);

        IntStream.range(0, 10).forEach(i -> {
            slowdown.record(now, Duration.ofMillis(10), 503);
            later(Duration.ofSeconds(11));
        });

        assertThat(slowdown.rate()).isEqualTo(1);
    }

    @Test
    void oncePromptAgainTheRateDoublesBackToTheSettingAndNoFurther() {
        Slowdown slowdown = new Slowdown(10);
        calls(slowdown, 20, 100);
        slowdown.record(now, Duration.ofMillis(100), 429);
        later(Duration.ofMinutes(2));

        IntStream.range(0, 3).forEach(i -> {
            calls(slowdown, 3, 110);
            later(Duration.ofSeconds(11));
        });

        assertThat(slowdown.rate()).isEqualTo(10);
    }

    @Test
    void theTargetHalvesOnARateLimitAndKeepsTheRateOtherwise() {
        Slowdown slowdown = new Slowdown(10);

        assertAll(
                () -> assertThat(slowdown.target(429)).isEqualTo(5),
                () -> assertThat(slowdown.target(200)).isEqualTo(10));
    }
}
