package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code key=value} pairs read from an instruction's text, in order. rewrite-docker 8.90.4 folds
 * {@code LABEL a=$X b=$Y} into one pair, so labels are read here (Phase 5, D5). Words split on whitespace outside
 * double quotes; quotes are removed and a backslash escapes the next character.
 */
final class Pairs {

    private Pairs() {
    }

    /**
     * Docker's quoting: inside {@code "..."} a backslash escapes; inside {@code '...'} nothing does. A literal dollar,
     * single-quoted or escaped, is kept as {@code \$}, the model's form for it.
     */
    static Map<String, String> parse(String text) {
        Map<String, String> pairs = new LinkedHashMap<>();
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean inKey = true;
        char quote = 0;
        boolean escaped = false;
        for (char c : (text + " ").toCharArray()) {
            StringBuilder current = inKey ? key : value;
            if (quote == '\'') {
                if (c == '\'') {
                    quote = 0;
                } else {
                    current.append(c == '$' ? "\\$" : String.valueOf(c));
                }
            } else if (escaped) {
                current.append(c == '$' ? "\\$" : String.valueOf(c));
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
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
}
