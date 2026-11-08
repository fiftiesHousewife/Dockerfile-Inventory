package io.github.fiftieshousewife.dockerfile.model;

/** A {@code STOPSIGNAL}: the signal the runtime sends to stop the container. */
public record StopSignal(String signal) implements Instruction {

    public StopSignal {
        Checks.notBlank(signal, "a stop signal");
    }
}
