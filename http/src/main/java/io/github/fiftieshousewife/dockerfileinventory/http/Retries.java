package io.github.fiftieshousewife.dockerfileinventory.http;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.function.ContextualSupplier;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/** Sends a call again, through Failsafe, as long as a {@link RetryPolicy} says it is worth it. */
public final class Retries {

    /** One attempt at a call. */
    @FunctionalInterface
    public interface Call {

        /**
         * Makes the call once.
         *
         * @return the server's answer
         * @throws IOException when no answer came
         * @throws InterruptedException when interrupted
         */
        HttpResponse<String> attempt() throws IOException, InterruptedException;
    }

    private Retries() {
    }

    /**
     * Makes the call until it is answered with something not worth retrying, or the attempts run out.
     *
     * @param policy which answers to retry, how long to wait and how many attempts
     * @param call one attempt
     * @param told each wait before another attempt
     * @return the last answer
     * @throws IOException the last network error, when every attempt failed with one
     * @throws InterruptedException when interrupted
     */
    public static HttpResponse<String> send(RetryPolicy policy, Call call, Consumer<Duration> told)
            throws IOException, InterruptedException {
        try {
            return Failsafe.with(dev.failsafe.RetryPolicy.<HttpResponse<String>>builder()
                    .handle(IOException.class)
                    .handleResultIf(response -> policy.retryable(response.statusCode(), response.headers()))
                    .withMaxAttempts(policy.attempts())
                    .withDelayFn(delay(policy))
                    .onRetryScheduled(event -> told.accept(event.getDelay()))
                    .build()).get(call::attempt);
        } catch (FailsafeException e) {
            throw unwrapped(e);
        }
    }

    static ContextualSupplier<HttpResponse<String>, Duration> delay(RetryPolicy policy) {
        return context -> context.getLastResult() == null ? policy.backoff(context.getAttemptCount())
                : policy.delay(context.getLastResult().statusCode(), context.getLastResult().headers(),
                        context.getAttemptCount(), Instant.now());
    }

    static IOException unwrapped(FailsafeException e) throws InterruptedException {
        if (e.getCause() instanceof InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
        if (e.getCause() instanceof IOException error) {
            return error;
        }
        throw e;
    }
}
