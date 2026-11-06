package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a shell-form {@code RUN} body as simple commands, each a list of words: {@link ShellWords} settles quotes,
 * escapes and substitutions, and {@link ShellCommands} separators, compound commands and assignments. A script this
 * reader cannot follow (a pipe into a shell, a heredoc, {@code select}) is empty, which callers count rather than
 * guess at.
 */
public final class ShellTokeniser {

    private ShellTokeniser() {
    }

    public static Optional<List<List<String>>> parse(String script) {
        return read(script).map(ShellCommands::commands);
    }

    /**
     * The variables a script sets with leading assignments ({@code INSTALL_PKGS="a b" && yum install $INSTALL_PKGS}),
     * quotes removed, later ones winning; empty when the script cannot be read.
     */
    public static Map<String, String> assignments(String script) {
        return read(script).map(ShellCommands::assignments).orElse(Map.of());
    }

    private static Optional<ShellCommands> read(String script) {
        return ShellWords.of(script).flatMap(ShellCommands::read);
    }
}
