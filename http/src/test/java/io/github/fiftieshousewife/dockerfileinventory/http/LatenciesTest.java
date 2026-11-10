package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LatenciesTest {

    private static Latencies firstCalls(long millis) {
        Latencies latencies = new Latencies();
        IntStream.range(0, Latencies.FIRST_CALLS).forEach(i -> latencies.add(0, Duration.ofMillis(millis)));
        return latencies;
    }

    @Test
    void theRunsPaceIsKnownOnlyOnceItsFirstCallsAreIn() {
        Latencies latencies = new Latencies();
        latencies.add(0, Duration.ofMillis(100));

        assertAll(
                () -> assertThat(latencies.known()).isFalse(),
                () -> assertThat(firstCalls(100).known()).isTrue());
    }

    @Test
    void nothingIsSlowBeforeTheRunsPaceIsKnown() {
        Latencies latencies = new Latencies();
        latencies.add(0, Duration.ofSeconds(10));

        assertThat(latencies.slowerThan(2)).isFalse();
    }

    @Test
    void theLastMinutesMedianIsComparedWithTheFirstCalls() {
        Latencies latencies = firstCalls(100);
        IntStream.range(0, 30).forEach(i -> latencies.add(Latencies.MINUTE * 2, Duration.ofMillis(250)));

        assertAll(
                () -> assertThat(latencies.slowerThan(2)).isTrue(),
                () -> assertThat(latencies.slowerThan(3)).isFalse());
    }

    @Test
    void theMedianIsTheMiddleValue() {
        assertAll(
                () -> assertThat(Latencies.median(List.of(5L, 1L, 3L))).isEqualTo(3),
                () -> assertThat(Latencies.median(List.of(4L, 1L, 3L, 2L))).isEqualTo(3));
    }
}
