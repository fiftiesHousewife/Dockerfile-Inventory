package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

record Waiter(int attempts, Duration every) {

    static Waiter immediate() {
        return new Waiter(1, Duration.ZERO);
    }

    void until(String what, BooleanSupplier up) {
        if (IntStream.range(0, attempts).noneMatch(attempt -> up.getAsBoolean() || pause())) {
            throw new IllegalStateException(what + " did not come up");
        }
    }

    boolean pause() {
        try {
            Thread.sleep(every);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
