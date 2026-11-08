package io.github.fiftieshousewife.dockerfile.reader.internal;

sealed interface ShellToken {

    // A private-use character standing for a single-quoted or escaped dollar, so assignments do not expand it.
    char LITERAL_DOLLAR = '';

    record Word(String marked, boolean quoted) implements ShellToken {

        String text() {
            return marked.replace(LITERAL_DOLLAR, '$');
        }
    }

    record FunctionParens(String name) implements ShellToken {
    }

    enum Operator implements ShellToken {
        NEWLINE, SEMICOLON, CASE_BREAK, AND_OR, PIPE, OPEN, CLOSE
    }
}
