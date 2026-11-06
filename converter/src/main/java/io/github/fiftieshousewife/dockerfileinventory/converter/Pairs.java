package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code key=value} pairs read from an instruction's text, in order. rewrite-docker 8.90.4 folds
 * {@code LABEL a=$X b=$Y} into one pair, so labels are read here (Phase 5, D5). Words split on whitespace outside
 * quotes; quotes are removed, and escapes are read as {@link #parse} says.
 */
final class Pairs {

    private Pairs() {
    }

    /**
     * Docker's quoting, into the model's form for a value: the text as it would be written inside double quotes, so
     * {@code $NAME} expands, {@code \$} is a literal dollar and {@code \\} a literal backslash. Inside {@code "..."}
     * a backslash escapes only a quote, a backslash or a dollar (and joins a continued line); before anything else
     * it is a literal backslash, kept as {@code \\\\} like any other, so one value has one form. Inside {@code '...'} nothing is special. Unquoted, a backslash escapes the next character.
     */
    static Map<String, String> parse(String text) {
        Map<String, String> pairs = new LinkedHashMap<>();
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean inKey = true;
        char quote = 0;
        String rest = text + " ";
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            char next = i + 1 < rest.length() ? rest.charAt(i + 1) : 0;
            StringBuilder current = inKey ? key : value;
            if (quote == '\'') {
                if (c == '\'') {
                    quote = 0;
                } else {
                    current.append(literal(c));
                }
            } else if (c == '\\' && quote == '"') {
                i += escapedInDoubleQuotes(next, current);
            } else if (c == '\\') {
                current.append(literal(next));
                i++;
            } else if (c == '"') {
                quote = quote == '"' ? 0 : '"';
            } else if (c == '\'' && quote == 0) {
                quote = '\'';
            } else if (quote == 0 && Character.isWhitespace(c)) {
                if (!key.isEmpty()) {
                    pairs.put(key.toString(), value.toString());
                }
                key.setLength(0);
                value.setLength(0);
                inKey = true;
            } else if (quote == 0 && c == '=' && inKey) {
                inKey = false;
            } else {
                current.append(c);
            }
        }
        return pairs;
    }

    /** A character that is itself, written in the model's form: a dollar or a backslash escaped. */
    private static String literal(char c) {
        return c == '$' || c == '\\' ? "\\" + c : String.valueOf(c);
    }

    /** After a backslash inside double quotes: appends what it means; returns how many characters it took. */
    private static int escapedInDoubleQuotes(char next, StringBuilder current) {
        switch (next) {
            case '"' -> current.append('"');
            case '\\', '$' -> current.append('\\').append(next);
            case '\n' -> {
            }
            default -> {
                current.append("\\\\");
                return 0;
            }
        }
        return 1;
    }
}
