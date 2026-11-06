package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Keys of {@code key=value} pairs read from text. rewrite-docker 8.90.4 folds {@code LABEL a=$X b=$Y} into one pair
 * {@code a}, so label keys are read from the printed instruction instead. Words split on whitespace outside double
 * quotes; quotes are removed and a backslash escapes the next character.
 */
final class Pairs {

    private Pairs() {
    }

    static List<String> keys(String pairs) {
        List<String> keys = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        boolean inKey = true;
        boolean quoted = false;
        boolean escaped = false;
        for (char c : pairs.toCharArray()) {
            if (escaped) {
                escaped = false;
                if (inKey) {
                    key.append(c);
                }
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && Character.isWhitespace(c)) {
                addIfAny(keys, key);
                inKey = true;
            } else if (!quoted && c == '=' && inKey) {
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
