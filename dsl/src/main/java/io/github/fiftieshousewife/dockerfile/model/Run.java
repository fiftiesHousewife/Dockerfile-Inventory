package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

public record Run(RunBody body, List<RunFlag> flags) implements Instruction {

    public Run {
        flags = List.copyOf(flags);
    }

    public Run(RunBody body) {
        this(body, List.of());
    }
}
