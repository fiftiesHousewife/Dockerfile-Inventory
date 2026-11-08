package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

public record Cmd(List<String> command, CommandForm form) implements Instruction {

    public Cmd {
        command = List.copyOf(command);
        Checks.oneStringInShellForm(command, form);
    }

    public Cmd(List<String> command) {
        this(command, CommandForm.EXEC);
    }
}
