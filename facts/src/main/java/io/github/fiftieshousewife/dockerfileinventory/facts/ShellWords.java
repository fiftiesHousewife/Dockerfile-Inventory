package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The first half of reading a shell-form {@code RUN}: characters to words and operators. Quotes, backslash escapes
 * and continuations, comments, and command substitutions (one word whose value is unknown here) are settled here;
 * what the words mean is {@link ShellCommands}'s job. A heredoc, an unclosed quote or substitution, or a parenthesis
 * glued to a word makes the script unreadable (empty).
 */
final class ShellWords {

    /**
     * Stands for a dollar that is literal (single-quoted or escaped) inside a word, so an assignment's value expands
     * only the shell's own references; it is a private-use character, so no script holds it.
     */
    static final char LITERAL_DOLLAR = '';

    /** Inside double quotes a backslash escapes only these, as in the shell; before anything else it is kept. */
    private static final String ESCAPED_IN_DOUBLE_QUOTES = "\"\\$`";

    sealed interface Token {
    }

    /** A word as read, literal dollars marked, and whether any part of it was quoted (so it is no redirection). */
    record Word(String marked, boolean quoted) implements Token {

        String text() {
            return marked.replace(LITERAL_DOLLAR, '$');
        }
    }

    /** {@code name()}, the start of a function definition; the name is empty for a bare {@code ()}. */
    record FunctionParens(String name) implements Token {
    }

    enum Operator implements Token {
        NEWLINE, SEMICOLON, CASE_BREAK, AND_OR, PIPE, OPEN, CLOSE
    }

    private final String script;
    private final List<Token> tokens = new ArrayList<>();
    private final StringBuilder word = new StringBuilder();
    private boolean quoted;
    private boolean unreadable;
    private int at;

    private ShellWords(String script) {
        this.script = script;
    }

    static Optional<List<Token>> of(String script) {
        return new ShellWords(script).read();
    }

    private Optional<List<Token>> read() {
        for (at = 0; at < script.length() && !unreadable; at++) {
            step(script.charAt(at));
        }
        endWord();
        return unreadable ? Optional.empty() : Optional.of(List.copyOf(tokens));
    }

    private void step(char c) {
        switch (c) {
            case '\\' -> escaped();
            case '\'' -> singleQuoted();
            case '"' -> doubleQuoted();
            case '`' -> backticks();
            case '$' -> dollar();
            case '(' -> openParenthesis();
            case ')' -> operator(Operator.CLOSE);
            case '\n' -> operator(Operator.NEWLINE);
            case ';' -> operator(doubled(';') ? Operator.CASE_BREAK : Operator.SEMICOLON);
            case '|' -> operator(doubled('|') ? Operator.AND_OR : Operator.PIPE);
            case '&' -> ampersand();
            case '<' -> lessThan();
            case '#' -> hash();
            default -> other(c);
        }
    }

    /** A backslash outside quotes: before a newline a continuation, removed; before anything else, that character. */
    private void escaped() {
        if (at + 1 < script.length() && next() != '\n') {
            word.append(literal(next()));
        }
        at++;
    }

    /** Everything up to the closing quote, taken as it is. */
    private void singleQuoted() {
        int close = script.indexOf('\'', at + 1);
        if (close < 0) {
            unreadable = true;
            return;
        }
        word.append(script.substring(at + 1, close).replace('$', LITERAL_DOLLAR));
        quoted = true;
        at = close;
    }

    /** Up to the closing quote, with the shell's escapes and substitutions; a backslash-newline is removed. */
    private void doubleQuoted() {
        for (at++; at < script.length() && !unreadable; at++) {
            char c = script.charAt(at);
            if (c == '"') {
                quoted = true;
                return;
            }
            switch (c) {
                case '\\' -> escapedInDoubleQuotes();
                case '$' -> dollar();
                case '`' -> backticks();
                default -> word.append(c);
            }
        }
        unreadable = true;
    }

    private void escapedInDoubleQuotes() {
        if (at + 1 == script.length()) {
            unreadable = true;
        } else if (next() == '\n') {
            at++;
        } else if (ESCAPED_IN_DOUBLE_QUOTES.indexOf(next()) >= 0) {
            word.append(literal(next()));
            at++;
        } else {
            word.append('\\');
        }
    }

    private void dollar() {
        if (next() == '(') {
            appendThrough(substitutionEnd());
        } else {
            word.append('$');
        }
    }

    private void backticks() {
        appendThrough(script.indexOf('`', at + 1));
    }

    /** A substitution appended as written, through its closing character; unclosed ({@code -1}) is unreadable. */
    private void appendThrough(int close) {
        if (close < 0) {
            unreadable = true;
            return;
        }
        word.append(script, at, close + 1);
        at = close;
    }

    /** The index of the parenthesis closing the {@code $(} at the current position, nested or quoted inside, or -1. */
    private int substitutionEnd() {
        int depth = 0;
        for (int i = at + 1; i < script.length(); i++) {
            char c = script.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '\'' || c == '"') {
                int close = script.indexOf(c, i + 1);
                if (close < 0) {
                    return -1;
                }
                i = close;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** {@code name()} opens a function, a bare {@code (} a subshell; glued to a word it is something unread. */
    private void openParenthesis() {
        if (next() == ')') {
            tokens.add(new FunctionParens(word.toString()));
            word.setLength(0);
            quoted = false;
            at++;
        } else if (word.isEmpty()) {
            operator(Operator.OPEN);
        } else {
            unreadable = true;
        }
    }

    private void ampersand() {
        if (doubled('&')) {
            operator(Operator.AND_OR);
        } else {
            word.append('&');
        }
    }

    /** A heredoc's body is not shell this reader can follow; a single {@code <} is a redirection, kept in the word. */
    private void lessThan() {
        if (next() == '<') {
            unreadable = true;
        } else {
            word.append('<');
        }
    }

    /** A comment, at the start of a word, runs to the end of its line; inside a word it is the character. */
    private void hash() {
        if (word.isEmpty()) {
            int newline = script.indexOf('\n', at);
            at = newline < 0 ? script.length() : newline - 1;
        } else {
            word.append('#');
        }
    }

    private void other(char c) {
        if (Character.isWhitespace(c)) {
            endWord();
        } else {
            word.append(c);
        }
    }

    /** Whether the next character repeats this one; if so it is consumed. */
    private boolean doubled(char c) {
        boolean doubled = next() == c;
        at += doubled ? 1 : 0;
        return doubled;
    }

    private void operator(Operator operator) {
        endWord();
        tokens.add(operator);
    }

    private void endWord() {
        if (!word.isEmpty()) {
            tokens.add(new Word(word.toString(), quoted));
            word.setLength(0);
        }
        quoted = false;
    }

    private char next() {
        return at + 1 < script.length() ? script.charAt(at + 1) : 0;
    }

    /** A character taken as itself: a dollar marked so that assignments do not expand it. */
    private static char literal(char c) {
        return c == '$' ? LITERAL_DOLLAR : c;
    }
}
