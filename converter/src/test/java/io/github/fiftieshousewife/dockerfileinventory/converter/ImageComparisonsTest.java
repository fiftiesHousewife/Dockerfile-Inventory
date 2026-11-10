package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ImageComparisonsTest {

    private static ImageComparison.Result result(String sha) {
        return ImageComparison.notBuilt(sha, "x");
    }

    @Test
    void theResultsComeInTheChosenOrderWhateverFinishesFirst() {
        CountDownLatch lastStarted = new CountDownLatch(1);

        List<ImageComparisons.Timed> timed = ImageComparisons.run(List.of("a", "b"), sha -> sha, 2, sha -> {
            if (sha.equals("b")) {
                lastStarted.countDown();
            } else {
                await(lastStarted);
            }
            return result(sha);
        }, System::nanoTime);

        assertThat(timed).extracting(t -> t.result().blobSha()).containsExactly("a", "b");
    }

    @Test
    void asManyRunAtOnceAsTheThreadsAllow() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger most = new AtomicInteger();

        ImageComparisons.run(List.of("a", "b", "c", "d", "e", "f"), sha -> sha, 3, sha -> {
            most.accumulateAndGet(running.incrementAndGet(), Math::max);
            sleep();
            running.decrementAndGet();
            return result(sha);
        }, System::nanoTime);

        assertThat(most.get()).isEqualTo(3);
    }

    @Test
    void thoseSharingACheckoutRunOneAfterAnother() {
        Map<String, Integer> runningPerCheckout = new ConcurrentHashMap<>();
        AtomicInteger most = new AtomicInteger();

        ImageComparisons.run(List.of("a1", "a2", "a3", "b1"), sha -> sha.substring(0, 1), 4, sha -> {
            most.accumulateAndGet(runningPerCheckout.merge(sha.substring(0, 1), 1, Integer::sum), Math::max);
            sleep();
            runningPerCheckout.merge(sha.substring(0, 1), -1, Integer::sum);
            return result(sha);
        }, System::nanoTime);

        assertThat(most.get()).isEqualTo(1);
    }

    @Test
    void eachResultIsTimedInWholeSeconds() {
        AtomicLong clock = new AtomicLong();

        List<ImageComparisons.Timed> timed = ImageComparisons.run(List.of("a"), sha -> sha, 1, sha -> {
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(2_600));
            return result(sha);
        }, clock::get);

        assertThat(timed.getFirst().seconds()).isEqualTo(3);
    }

    @Test
    void theParallelSettingDefaultsToFourAndRefusesWhatIsNotAPositiveNumber() {
        assertAll(
                () -> assertThat(ImageComparisons.parallel(Map.of())).isEqualTo(4),
                () -> assertThat(ImageComparisons.parallel(Map.of("IMAGE_ROUND_TRIP_PARALLEL", "2"))).isEqualTo(2),
                () -> assertThatThrownBy(() -> ImageComparisons.parallel(Map.of("IMAGE_ROUND_TRIP_PARALLEL", "0")))
                        .hasMessage("IMAGE_ROUND_TRIP_PARALLEL must be a positive number, not 0"),
                () -> assertThatThrownBy(() -> ImageComparisons.parallel(Map.of("IMAGE_ROUND_TRIP_PARALLEL", "x")))
                        .hasMessage("IMAGE_ROUND_TRIP_PARALLEL must be a positive number, not x"));
    }

    @Test
    void aComparisonThatThrowsFailsTheRun() {
        assertThatThrownBy(() -> ImageComparisons.run(List.of("a"), sha -> sha, 1, sha -> {
            throw new IllegalArgumentException("broken");
        }, System::nanoTime)).hasRootCauseMessage("broken");
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
