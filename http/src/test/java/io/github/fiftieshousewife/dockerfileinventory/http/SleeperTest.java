package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SleeperTest {

    @Test
    void theRealSleeperWaitsWithoutFailing() {
        assertThatCode(() -> Sleeper.REAL.sleep(Duration.ofMillis(1))).doesNotThrowAnyException();
    }
}
