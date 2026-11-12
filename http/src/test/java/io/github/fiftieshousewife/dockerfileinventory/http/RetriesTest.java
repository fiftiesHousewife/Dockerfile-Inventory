package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import dev.failsafe.FailsafeException;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetriesTest {

    private static final RetryPolicy THREE = new RetryPolicy(3, Duration.ofHours(1), Duration.ZERO);

    private final List<Duration> waits = new ArrayList<>();

    @Test
    void aNetworkErrorThenAnAnswerIsTheAnswer() throws Exception {
        assertThat(Retries.send(THREE, answers(new IOException("reset"), CannedResponse.of(200, "ok")), waits::add)
                .body()).isEqualTo("ok");
    }

    @Test
    void aGatewayErrorThenAnAnswerIsTheAnswer() throws Exception {
        assertThat(Retries.send(THREE, answers(CannedResponse.of(502, ""), CannedResponse.of(200, "ok")), waits::add)
                .statusCode()).isEqualTo(200);
    }

    @Test
    void theAttemptsSpentOnGatewayErrorsReturnTheLast() throws Exception {
        assertThat(Retries.send(THREE, answers(CannedResponse.of(502, "1"), CannedResponse.of(502, "2"),
                CannedResponse.of(502, "3"), CannedResponse.of(200, "never")), waits::add).body()).isEqualTo("3");
    }

    @Test
    void theAttemptsSpentOnNetworkErrorsThrowTheLast() {
        assertThatExceptionOfType(IOException.class).isThrownBy(() -> Retries.send(THREE, answers(
                new IOException("1"), new IOException("2"), new IOException("3")), waits::add)).withMessage("3");
    }

    @Test
    void anAnswerNotWorthRetryingIsReturnedAtOnce() throws Exception {
        assertThat(Retries.send(THREE, answers(CannedResponse.of(404, "gone"), CannedResponse.of(200, "never")),
                waits::add).body()).isEqualTo("gone");
    }

    @Test
    void aRateLimitBeyondTheLongestWaitStopsTheRun() {
        HttpResponse<String> limited = CannedResponse.of(403, "", Map.of("x-ratelimit-remaining", List.of("0"),
                "x-ratelimit-reset", List.of(String.valueOf(Instant.now().plusSeconds(7200).getEpochSecond()))));

        assertThatExceptionOfType(RateLimitWaitTooLong.class)
                .isThrownBy(() -> Retries.send(THREE, answers(limited), waits::add));
    }

    @Test
    void eachWaitIsTold() throws Exception {
        Retries.send(THREE, answers(CannedResponse.of(429, "", Map.of("retry-after", List.of("0"))),
                new IOException("reset"), CannedResponse.of(200, "ok")), waits::add);

        assertThat(waits).containsExactly(Duration.ZERO, Duration.ZERO);
    }

    @Test
    void failsafesWrappingIsTakenOff() throws InterruptedException {
        IOException error = new IOException("reset");

        assertThat(Retries.unwrapped(new FailsafeException(error))).isSameAs(error);
    }

    @Test
    void anInterruptedWaitIsThrownAsAnInterruption() {
        assertAll(
                () -> assertThatExceptionOfType(InterruptedException.class)
                        .isThrownBy(() -> Retries.unwrapped(new FailsafeException(new InterruptedException()))),
                () -> assertThat(Thread.interrupted()).isTrue());
    }

    @Test
    void anyOtherFailureIsThrownAsItCame() {
        FailsafeException other = new FailsafeException(new Exception("odd"));

        assertThatExceptionOfType(FailsafeException.class).isThrownBy(() -> Retries.unwrapped(other))
                .isSameAs(other);
    }

    private static Retries.Call answers(Object... answers) {
        Deque<Object> left = new ArrayDeque<>(List.of(answers));
        return () -> {
            Object next = left.removeFirst();
            if (next instanceof IOException error) {
                throw error;
            }
            @SuppressWarnings("unchecked")
            HttpResponse<String> response = (HttpResponse<String>) next;
            return response;
        };
    }
}
