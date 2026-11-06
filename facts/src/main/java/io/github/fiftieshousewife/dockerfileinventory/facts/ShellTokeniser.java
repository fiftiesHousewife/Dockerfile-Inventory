package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Splits a shell-form {@code RUN} body into simple commands, each a list of words. It understands only the
 * subset Dockerfiles mostly use: words, quotes, backslash escapes and continuations, comments, redirections
 * (dropped with their targets), and the separators newline,
 * {@code &&}, {@code ||}, {@code ;} and pipes. Anything else (control flow, subshells, command substitution, groups,
 * functions, heredocs, a pipe into a shell) makes the whole body opaque (empty), which callers count rather than guess at.
 */
public final class ShellTokeniser {

    /** {@code NAME=value} before a command's name sets its environment; it is not the command. */
    private static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=.*", Pattern.DOTALL);

    /** A redirection operator, optionally with its target joined on ({@code >/dev/null}, {@code 2>&1}). */
    private static final Pattern REDIRECT = Pattern.compile("\\d*(&>>?|>>?|<)(.*)");

    /** First words that open compound commands. */
    private static final Set<String> COMPOUND = Set.of(
            "if", "then", "elif", "else", "fi", "case", "esac", "for", "select", "while", "until", "do", "done",
            "function", "{", "}");

    /** A pipe into one of these runs downloaded or generated code nobody can read here. */
    private static final Set<String> SHELLS = Set.of("sh", "bash", "ash", "dash", "zsh", "ksh");

    private final String script;
    private final List<List<String>> commands = new ArrayList<>();
    private List<String> command = new ArrayList<>();
    private final StringBuilder word = new StringBuilder();
    private boolean opaque;
    private boolean pipedInto;
    private boolean targetNext;
    private boolean wordQuoted;

    private final Map<String, String> assignments = new LinkedHashMap<>();

    private ShellTokeniser(String script) {
        this.script = script;
    }

    public static Optional<List<List<String>>> parse(String script) {
        return new ShellTokeniser(script).run();
    }

    /**
     * The variables a script sets with leading assignments ({@code INSTALL_PKGS="a b" && yum install $INSTALL_PKGS}),
     * quotes removed, later ones winning; empty when the script is opaque.
     */
    public static Map<String, String> assignments(String script) {
        ShellTokeniser tokeniser = new ShellTokeniser(script);
        return tokeniser.run().isPresent() ? Map.copyOf(tokeniser.assignments) : Map.of();
    }

    private Optional<List<List<String>>> run() {
        for (int i = 0; i < script.length() && !opaque; i++) {
            char c = script.charAt(i);
            if (c == '\\') {
                if (next(i) != '\n') {
                    word.append(next(i));
                }
                i++;
            } else if (c == '\'' || c == '"') {
                i = quoted(i, c);
            } else if (c == '(' || c == ')' || c == '`' || (c == '<' && next(i) == '<')) {
                opaque = true;
            } else if (c == '#' && word.isEmpty()) {
                i = script.indexOf('\n', i) < 0 ? script.length() : script.indexOf('\n', i) - 1;
            } else if (c == '\n') {
                endCommand();
            } else if (Character.isWhitespace(c)) {
                endWord();
            } else if (c == ';') {
                endCommand();
            } else if ((c == '&' || c == '|') && next(i) == c) {
                endCommand();
                i++;
            } else if (c == '|') {
                endCommand();
                pipedInto = true;
            } else {
                word.append(c);
            }
        }
        endCommand();
        return opaque ? Optional.empty() : Optional.of(List.copyOf(commands));
    }

    /** Appends a quoted span to the current word, without its quotes; returns the index of the closing quote. */
    private int quoted(int open, char quote) {
        int close = script.indexOf(quote, open + 1);
        if (close < 0) {
            opaque = true;
            return script.length();
        }
        String span = script.substring(open + 1, close);
        if (quote == '"' && (span.contains("$(") || span.contains("`") || span.endsWith("\\"))) {
            opaque = true;
        }
        word.append(span);
        wordQuoted = true;
        return close;
    }

    private char next(int i) {
        return i + 1 < script.length() ? script.charAt(i + 1) : 0;
    }

    private void endWord() {
        if (!word.isEmpty()) {
            String w = word.toString();
            boolean quoted = wordQuoted;
            word.setLength(0);
            wordQuoted = false;
            var redirect = REDIRECT.matcher(w);
            if (targetNext) {
                targetNext = false;
            } else if (!quoted && redirect.matches()) {
                targetNext = redirect.group(2).isEmpty();
            } else if (command.isEmpty() && ASSIGNMENT.matcher(w).matches()) {
                assignments.put(w.substring(0, w.indexOf('=')), w.substring(w.indexOf('=') + 1));
            } else {
                command.add(w);
            }
        }
    }

    private void endCommand() {
        endWord();
        if (!command.isEmpty()) {
            if (COMPOUND.contains(command.getFirst()) || (pipedInto && SHELLS.contains(command.getFirst()))) {
                opaque = true;
            }
            commands.add(List.copyOf(command));
            command = new ArrayList<>();
            pipedInto = false;
        }
    }
}
