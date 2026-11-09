package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WaiterTest {

    @Test
    void aWaitGivesUpAfterItsAttemptsSaying() {
        assertThatIllegalStateException().isThrownBy(() -> Waiter.immediate().until("JCR", () -> false))
                .withMessage("JCR did not come up");
    }

    @Test
    void aWaitAsksAgainUntilTheConditionHolds() {
        AtomicInteger asked = new AtomicInteger();

        new Waiter(5, Duration.ZERO).until("JCR", () -> asked.incrementAndGet() == 3);

        assertThat(asked).hasValue(3);
    }

    @Test
    void aPauseNeverEndsTheWait() {
        assertThat(Waiter.immediate().pause()).isFalse();
    }

    @Test
    void anInterruptedPauseStopsTheWaitAndKeepsTheInterrupt() {
        Thread.currentThread().interrupt();

        assertAll(
                () -> assertThatIllegalStateException().isThrownBy(() -> new Waiter(1, Duration.ofSeconds(5)).pause()),
                () -> assertThat(Thread.interrupted()).isTrue());
    }
}
