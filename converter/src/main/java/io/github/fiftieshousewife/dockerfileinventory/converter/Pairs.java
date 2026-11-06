package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.facts.KeyValuePairs;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code key=value} pairs of an {@code ENV}, {@code LABEL} or {@code ARG}, with each value in the model's form. The
 * words and keys come from facts' {@link KeyValuePairs}; this turns a value as written into the model's form.
 */
final class Pairs {

    private Pairs() {
    }

    /** In order; a key written twice keeps its last value, as Docker does; a word without {@code =} has "". */
    static Map<String, String> parse(String text) {
        Map<String, String> pairs = new LinkedHashMap<>();
        KeyValuePairs.of(text).forEach(p -> pairs.put(p.key(), p.value() == null ? "" : modelForm(p.value())));
        return pairs;
    }

    /**
     * Docker's quoting, into the model's form for a value: the text as it would be written inside double quotes, so
     * {@code $NAME} expands, {@code \$} is a literal dollar and {@code \\} a literal backslash. Inside {@code "..."}
     * a backslash escapes only a quote, a backslash or a dollar (and joins a continued line); before anything else
     * it is a literal backslash, kept as {@code \\\\} like any other, so one value has one form. Inside
     * {@code '...'} nothing is special. Unquoted, a backslash escapes the next character.
     */
    static String modelForm(String written) {
        StringBuilder value = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < written.length(); i++) {
            char c = written.charAt(i);
            char next = i + 1 < written.length() ? written.charAt(i + 1) : 0;
            if (quote == '\'') {
                quote = c == '\'' ? 0 : quote;
                value.append(c == '\'' ? "" : literal(c));
            } else if (c == '\\' && quote == '"') {
                i += escapedInDoubleQuotes(next, value);
            } else if (c == '\\') {
                value.append(literal(next));
                i++;
            } else if (c == '"') {
                quote = quote == '"' ? 0 : '"';
            } else if (c == '\'' && quote == 0) {
                quote = '\'';
            } else {
                value.append(c);
            }
        }
        return value.toString();
    }

    /** A character that is itself, written in the model's form: a dollar or a backslash escaped. */
    private static String literal(char c) {
        return c == '$' || c == '\\' ? "\\" + c : c == 0 ? "" : String.valueOf(c);
    }

    /** After a backslash inside double quotes: appends what it means; returns how many characters it took. */
    private static int escapedInDoubleQuotes(char next, StringBuilder value) {
        switch (next) {
            case '"' -> value.append('"');
            case '\\', '$' -> value.append('\\').append(next);
            case '\n' -> {
            }
            default -> {
                value.append("\\\\");
                return 0;
            }
        }
        return 1;
    }
}
