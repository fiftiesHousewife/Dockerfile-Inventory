package io.github.fiftieshousewife.dockerfile.reader.internal;

final class ShellScan {

    final String script;
    final StringBuilder word = new StringBuilder();
    int at;
    boolean quoted;
    boolean unreadable;

    ShellScan(String script) {
        this.script = script;
    }

    boolean more() {
        return at < script.length() && !unreadable;
    }

    char next() {
        return at + 1 < script.length() ? script.charAt(at + 1) : 0;
    }

    boolean doubled(char c) {
        boolean doubled = next() == c;
        at += doubled ? 1 : 0;
        return doubled;
    }

    void appendThrough(int close) {
        if (close < 0) {
            unreadable = true;
            return;
        }
        word.append(script, at, close + 1);
        at = close;
    }

    String takeWord() {
        String taken = word.toString();
        word.setLength(0);
        quoted = false;
        return taken;
    }
}
