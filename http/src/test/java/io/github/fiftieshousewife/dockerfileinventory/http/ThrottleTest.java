package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ThrottleTest {

    private final AtomicLong clock = new AtomicLong();
    private final List<Duration> waits = new ArrayList<>();
    private final Sleeper advancing = wait -> {
        waits.add(wait);
        clock.addAndGet(wait.toNanos());
    };

    private Throttle throttle(double perSecond) {
        return new Throttle(perSecond, clock::get, advancing);
    }

    private void later(Duration by) {
        clock.addAndGet(by.toNanos());
    }

    private static void calls(Throttle throttle, int count, long millis) {
        IntStream.range(0, count).forEach(i -> throttle.record(Duration.ofMillis(millis), 200));
    }

    @Test
    void eachRecordedCallIsCounted() {
        Throttle throttle = throttle(10);
        calls(throttle, 3, 10);

        assertThat(throttle.calls()).isEqualTo(3);
    }

    @Test
    void callsAreSpacedAtTheRate() throws InterruptedException {
        Throttle throttle = throttle(2);

        throttle.acquire();
        throttle.acquire();
        throttle.acquire();

        assertThat(waits).containsExactly(Duration.ofMillis(500), Duration.ofMillis(500));
    }

    @Test
    void aCallAfterAQuietSpellGoesAtOnce() throws InterruptedException {
        Throttle throttle = throttle(2);
        throttle.acquire();
        later(Duration.ofSeconds(5));

        throttle.acquire();

        assertThat(waits).isEmpty();
    }

    @Test
    void manyThreadsShareTheOneRate() throws InterruptedException {
        Throttle throttle = new Throttle(100, System::nanoTime, Sleeper.REAL);
        long start = System.nanoTime();

        List<Thread> threads = IntStream.range(0, 5).mapToObj(i -> Thread.ofVirtual().start(() -> {
            try {
                throttle.acquire();
                throttle.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })).toList();
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(85));
    }

    @Test
    void aRateLimitedAnswerSlowsTheCalls() {
        Throttle throttle = throttle(10);

        throttle.record(Duration.ofMillis(10), 429);

        assertThat(throttle.rate()).isEqualTo(5);
    }

    @Test
    void aRateThatIsNotPositiveIsRefused() {
        assertThatThrownBy(() -> new Throttle(0, clock::get, advancing))
                .hasMessage("requests per second must be above 0, not 0.0");
    }

    @Test
    void theRateIsReadFromItsSettingAndRefusedByNameWhenMissingOrNotAPositiveNumber() {
        assertAll(
                () -> assertThat(Throttle.rate(Map.of("GHE_MAX_REQUESTS_PER_SECOND", "2.5"),
                        "GHE_MAX_REQUESTS_PER_SECOND")).isEqualTo(2.5),
                () -> assertThatThrownBy(() -> Throttle.rate(Map.of(), "GHE_MAX_REQUESTS_PER_SECOND"))
                        .hasMessage("GHE_MAX_REQUESTS_PER_SECOND is required: the most calls a second"),
                () -> assertThatThrownBy(() -> Throttle.rate(Map.of("A", "fast"), "A"))
                        .hasMessage("A is a number of calls a second above 0, not fast"),
                () -> assertThatThrownBy(() -> Throttle.rate(Map.of("A", "0"), "A"))
                        .hasMessage("A is a number of calls a second above 0, not 0"));
    }

    @Test
    void aConcurrencyIsAPositiveWholeNumberRefusedByNameOrItsDefault() {
        assertAll(
                () -> assertThat(Throttle.concurrency(Map.of(), "GHE_CONCURRENCY", 8)).isEqualTo(8),
                () -> assertThat(Throttle.concurrency(Map.of("GHE_CONCURRENCY", "3"), "GHE_CONCURRENCY", 8))
                        .isEqualTo(3),
                () -> assertThatThrownBy(() -> Throttle.concurrency(Map.of("GHE_CONCURRENCY", "lots"),
                        "GHE_CONCURRENCY", 8)).hasMessage("GHE_CONCURRENCY is a positive whole number, not lots"));
    }
}
