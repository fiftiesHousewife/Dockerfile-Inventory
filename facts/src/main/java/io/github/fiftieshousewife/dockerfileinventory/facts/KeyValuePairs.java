package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code key=value} pairs read from an instruction's text, as Docker splits them: words end at whitespace outside
 * quotes, and a backslash outside single quotes escapes the next character. rewrite-docker 8.90.4 mis-reads pairs in
 * several layouts (upstream issues 4, 5, 6 and 12), so facts, rules and the converter all read them here.
 */
public final class KeyValuePairs {

    /** One word: its key, and its value as written (quotes and escapes kept), null when it has no {@code =}. */
    public record Pair(String key, String value) {
    }

    private KeyValuePairs() {
    }

    public static List<Pair> of(String text) {
        return words(text).stream().map(KeyValuePairs::pair).toList();
    }

    /** The keys, quotes removed, sorted. */
    public static List<String> keys(String text) {
        return of(text).stream().map(Pair::key).sorted().toList();
    }

    /** A value without its surrounding quotes, single or double. */
    public static String unquoted(String value) {
        boolean quoted = value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"));
        return quoted ? value.substring(1, value.length() - 1) : value;
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        Scan scan = new Scan();
        for (char c : text.toCharArray()) {
            if (scan.outside() && Character.isWhitespace(c)) {
                addIfAny(words, word);
            } else {
                word.append(c);
            }
            scan.step(c);
        }
        addIfAny(words, word);
        return words;
    }

    private static Pair pair(String word) {
        int equals = firstUnquotedEquals(word);
        return equals < 0 ? new Pair(plain(word), null)
                : new Pair(plain(word.substring(0, equals)), word.substring(equals + 1));
    }

    private static int firstUnquotedEquals(String word) {
        Scan scan = new Scan();
        for (int i = 0; i < word.length(); i++) {
            if (scan.outside() && word.charAt(i) == '=') {
                return i;
            }
            scan.step(word.charAt(i));
        }
        return -1;
    }

    /** A key as Docker reads it: quotes removed, escaped characters themselves. */
    private static String plain(String raw) {
        StringBuilder plain = new StringBuilder();
        Scan scan = new Scan();
        for (char c : raw.toCharArray()) {
            if (scan.literal(c)) {
                plain.append(c);
            }
            scan.step(c);
        }
        return plain.toString();
    }

    private static void addIfAny(List<String> words, StringBuilder word) {
        if (!word.isEmpty()) {
            words.add(word.toString());
            word.setLength(0);
        }
    }

    /** Where a scan stands: inside which quote, and whether the last character was an escaping backslash. */
    private static final class Scan {

        private char quote;
        private boolean escaped;

        boolean outside() {
            return quote == 0 && !escaped;
        }

        /** Whether {@code c} is text, not a quote that opens or closes or a backslash that escapes. */
        boolean literal(char c) {
            if (escaped) {
                return true;
            }
            if (quote == '\'') {
                return c != '\'';
            }
            return c != '\\' && c != '"' && !(c == '\'' && quote == 0);
        }

        void step(char c) {
            if (escaped) {
                escaped = false;
            } else if (quote == '\'') {
                quote = c == '\'' ? 0 : quote;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quote = quote == '"' ? 0 : '"';
            } else if (c == '\'' && quote == 0) {
                quote = '\'';
            }
        }
    }
}
