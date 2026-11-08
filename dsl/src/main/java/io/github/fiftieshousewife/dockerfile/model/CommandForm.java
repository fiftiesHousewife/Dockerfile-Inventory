package io.github.fiftieshousewife.dockerfile.model;

/** How a command is written: a JSON array run directly, or one string run by the shell. */
public enum CommandForm {
    EXEC,
    SHELL
}
