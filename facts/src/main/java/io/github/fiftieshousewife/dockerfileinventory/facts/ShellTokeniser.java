package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Splits a shell-form {@code RUN} body into simple commands, each a list of words. It understands only the
 * subset Dockerfiles mostly use; anything else makes the whole body opaque (empty), which callers count rather
 * than guess at.
 */
public final class ShellTokeniser {

    private final String script;
    private final List<List<String>> commands = new ArrayList<>();
    private List<String> command = new ArrayList<>();
    private final StringBuilder word = new StringBuilder();

    private ShellTokeniser(String script) {
        this.script = script;
    }

    public static Optional<List<List<String>>> parse(String script) {
        return new ShellTokeniser(script).run();
    }

    private Optional<List<List<String>>> run() {
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (Character.isWhitespace(c)) {
                endWord();
            } else if (c == ';') {
                endCommand();
            } else if ((c == '&' || c == '|') && next(i) == c) {
                endCommand();
                i++;
            } else {
                word.append(c);
            }
        }
        endCommand();
        return Optional.of(List.copyOf(commands));
    }

    private char next(int i) {
        return i + 1 < script.length() ? script.charAt(i + 1) : 0;
    }

    private void endWord() {
        if (!word.isEmpty()) {
            command.add(word.toString());
            word.setLength(0);
        }
    }

    private void endCommand() {
        endWord();
        if (!command.isEmpty()) {
            commands.add(List.copyOf(command));
            command = new ArrayList<>();
        }
    }
}
