package io.github.fiftieshousewife.dockerfile.model;

public record StopSignal(String signal) implements Instruction {

    public StopSignal {
        Checks.notBlank(signal, "a stop signal");
    }
}
