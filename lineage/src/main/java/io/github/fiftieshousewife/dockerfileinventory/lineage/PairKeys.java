package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.ArrayList;
import java.util.List;

final class PairKeys {

    private final List<String> keys = new ArrayList<>();
    private final StringBuilder key = new StringBuilder();
    private boolean inKey = true;
    private char quote;
    private boolean escaped;
    private char previous = ' ';

    private PairKeys() {
    }

    static List<String> of(String pairs) {
        PairKeys scan = new PairKeys();
        pairs.chars().forEach(c -> scan.accept((char) c));
        scan.endKey();
        return scan.keys.stream().sorted().toList();
    }

    // A quote opens only at the start of a key or value: the classic builder writes values unquoted, so MSG=it's
    // holds an apostrophe, not a quote.
    void accept(char c) {
        boolean opens = quote == 0 && (Character.isWhitespace(previous) || previous == '=');
        previous = c;
        if (quote == '\'') {
            singleQuoted(c);
        } else if (escaped) {
            escaped = false;
            keyChar(c);
        } else if (c == '\\') {
            escaped = true;
        } else if (!quoteMark(c, opens)) {
            plain(c);
        }
    }

    void singleQuoted(char c) {
        if (c == '\'') {
            quote = 0;
        } else {
            keyChar(c);
        }
    }

    boolean quoteMark(char c, boolean opens) {
        if (c == '"' && (quote == '"' || opens)) {
            quote = quote == '"' ? 0 : '"';
            return true;
        }
        if (c == '\'' && opens) {
            quote = '\'';
            return true;
        }
        return false;
    }

    void plain(char c) {
        if (quote == 0 && Character.isWhitespace(c)) {
            endKey();
            inKey = true;
        } else if (quote == 0 && c == '=' && inKey) {
            inKey = false;
        } else {
            keyChar(c);
        }
    }

    void keyChar(char c) {
        if (inKey) {
            key.append(c);
        }
    }

    void endKey() {
        if (!key.isEmpty()) {
            keys.add(key.toString());
            key.setLength(0);
        }
    }
}
