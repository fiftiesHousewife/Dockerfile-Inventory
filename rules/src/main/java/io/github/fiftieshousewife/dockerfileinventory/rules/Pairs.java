package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Keys of {@code key=value} pairs read from text. rewrite-docker 8.90.4 folds {@code LABEL a=$X b=$Y} into one pair
 * {@code a}, so label keys are read from the printed instruction instead. Words split on whitespace outside
 * quotes; quotes are removed and a backslash escapes the next character outside single quotes.
 */
final class Pairs {

    private Pairs() {
    }

    static List<String> keys(String pairs) {
        List<String> keys = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        boolean inKey = true;
        char quote = 0;
        boolean escaped = false;
        for (char c : pairs.toCharArray()) {
            if (quote == '\'') {
                if (c == '\'') {
                    quote = 0;
                } else if (inKey) {
                    key.append(c);
                }
            } else if (escaped) {
                escaped = false;
                if (inKey) {
                    key.append(c);
                }
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quote = quote == '"' ? 0 : '"';
            } else if (c == '\'' && quote == 0) {
                quote = '\'';
            } else if (quote == 0 && Character.isWhitespace(c)) {
                addIfAny(keys, key);
                inKey = true;
            } else if (quote == 0 && c == '=' && inKey) {
                inKey = false;
            } else if (inKey) {
                key.append(c);
            }
        }
        addIfAny(keys, key);
        return keys;
    }

    private static void addIfAny(List<String> keys, StringBuilder key) {
        if (!key.isEmpty()) {
            keys.add(key.toString());
            key.setLength(0);
        }
    }
}
