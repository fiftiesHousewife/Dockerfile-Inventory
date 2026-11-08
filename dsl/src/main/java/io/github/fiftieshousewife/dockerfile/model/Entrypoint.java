package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** Shell form only for a converted Dockerfile: the builders offer exec form, and H2 finds shell form. */
public record Entrypoint(List<String> command, CommandForm form) implements Instruction {

    public Entrypoint {
        command = List.copyOf(command);
        Checks.oneStringInShellForm(command, form);
    }

    /** In exec form. */
    public Entrypoint(List<String> command) {
        this(command, CommandForm.EXEC);
    }
}
