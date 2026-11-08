package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** A {@code CMD}: the entrypoint's default arguments, or the command when there is none. */
public record Cmd(List<String> command, CommandForm form) implements Instruction {

    public Cmd {
        command = List.copyOf(command);
        Checks.oneStringInShellForm(command, form);
    }

    /** In exec form. */
    public Cmd(List<String> command) {
        this(command, CommandForm.EXEC);
    }
}
