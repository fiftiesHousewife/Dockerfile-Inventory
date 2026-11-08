package io.github.fiftieshousewife.dockerfile.reader.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a shell-form {@code RUN} body as simple commands, so package calls can be found without running the shell. A
 * script it cannot follow (a pipe into a shell, a heredoc, {@code select}) is empty, which callers count rather than
 * guess at.
 */
public final class ShellTokeniser {

    private ShellTokeniser() {
    }

    /** Each simple command as its words, compound commands read for the commands inside them. */
    public static Optional<List<List<String>>> parse(String script) {
        return read(script).map(ShellCommands::commands);
    }

    /** The variables the script assigns, so a later {@code $INSTALL_PKGS} can be expanded; empty when unreadable. */
    public static Map<String, String> assignments(String script) {
        return read(script).map(ShellCommands::assignments).orElse(Map.of());
    }

    private static Optional<ShellCommands> read(String script) {
        return ShellWords.of(script).flatMap(ShellCommands::read);
    }
}
