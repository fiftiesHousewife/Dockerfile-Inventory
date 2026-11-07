package io.github.fiftieshousewife.dockerfileinventory.dsl;

/** How a command is written: a JSON array run directly, or one string run by the shell. */
public enum CommandForm {
    EXEC,
    SHELL
}
