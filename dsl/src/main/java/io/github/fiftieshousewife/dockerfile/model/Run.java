package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** A {@code RUN}: its body (an install, shell commands, an exec form or a heredoc) and its flags, such as mounts. */
public record Run(RunBody body, List<RunFlag> flags) implements Instruction {

    public Run {
        flags = List.copyOf(flags);
    }

    /** Without flags. */
    public Run(RunBody body) {
        this(body, List.of());
    }
}
