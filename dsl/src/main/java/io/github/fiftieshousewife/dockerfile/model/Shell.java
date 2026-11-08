package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** Exec form, as Docker requires for {@code SHELL}. */
public record Shell(List<String> command) implements Instruction {

    public Shell {
        command = List.copyOf(Checks.notEmpty(command, "a SHELL command"));
    }
}
