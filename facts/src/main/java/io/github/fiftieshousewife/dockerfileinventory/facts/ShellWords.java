package io.github.fiftieshousewife.dockerfileinventory.facts;

import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.FunctionParens;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.Operator;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.Word;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class ShellWords {

    private static final Pattern ARRAY_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=");

    private final ShellScan scan;
    private final List<ShellToken> tokens = new ArrayList<>();
    private final List<String> arrayWords = new ArrayList<>();
    private String arrayName;

    private ShellWords(String script) {
        this.scan = new ShellScan(script);
    }

    static Optional<List<ShellToken>> of(String script) {
        return new ShellWords(script).read();
    }

    Optional<List<ShellToken>> read() {
        for (scan.at = 0; scan.more(); scan.at++) {
            step(scan.script.charAt(scan.at));
        }
        endWord();
        return scan.unreadable || arrayName != null ? Optional.empty() : Optional.of(List.copyOf(tokens));
    }

    void step(char c) {
        switch (c) {
            case '\\' -> ShellQuotes.escaped(scan);
            case '\'' -> ShellQuotes.singleQuoted(scan);
            case '"' -> ShellQuotes.doubleQuoted(scan);
            case '`' -> ShellSubstitutions.backticks(scan);
            case '$' -> ShellSubstitutions.dollar(scan);
            case '(' -> openParenthesis();
            case ')' -> closeParenthesis();
            case '\n' -> newline();
            case ';' -> operator(scan.doubled(';') ? Operator.CASE_BREAK : Operator.SEMICOLON);
            case '|' -> operator(scan.doubled('|') ? Operator.AND_OR : Operator.PIPE);
            case '&' -> ampersand();
            case '<' -> lessThan();
            case '#' -> hash();
            default -> other(c);
        }
    }

    void openParenthesis() {
        if (arrayName == null && ARRAY_NAME.matcher(scan.word).matches()) {
            arrayName = scan.takeWord();
        } else if (scan.next() == ')') {
            tokens.add(new FunctionParens(scan.takeWord()));
            scan.at++;
        } else if (scan.word.isEmpty()) {
            operator(Operator.OPEN);
        } else {
            scan.unreadable = true;
        }
    }

    void closeParenthesis() {
        if (arrayName == null) {
            operator(Operator.CLOSE);
            return;
        }
        endWord();
        tokens.add(new Word(arrayName + String.join(" ", arrayWords), true));
        arrayName = null;
        arrayWords.clear();
    }

    void newline() {
        if (arrayName == null) {
            operator(Operator.NEWLINE);
        } else {
            endWord();
        }
    }

    void ampersand() {
        if (scan.doubled('&')) {
            operator(Operator.AND_OR);
        } else {
            scan.word.append('&');
        }
    }

    void lessThan() {
        if (scan.next() == '<') {
            scan.unreadable = true;
        } else {
            scan.word.append('<');
        }
    }

    void hash() {
        if (scan.word.isEmpty()) {
            int newline = scan.script.indexOf('\n', scan.at);
            scan.at = newline < 0 ? scan.script.length() : newline - 1;
        } else {
            scan.word.append('#');
        }
    }

    void other(char c) {
        if (Character.isWhitespace(c)) {
            endWord();
        } else {
            scan.word.append(c);
        }
    }

    private void operator(Operator operator) {
        endWord();
        tokens.add(operator);
    }

    void endWord() {
        boolean quoted = scan.quoted;
        String word = scan.takeWord();
        if (word.isEmpty()) {
            return;
        }
        if (arrayName != null) {
            arrayWords.add(word);
        } else {
            tokens.add(new Word(word, quoted));
        }
    }
}
