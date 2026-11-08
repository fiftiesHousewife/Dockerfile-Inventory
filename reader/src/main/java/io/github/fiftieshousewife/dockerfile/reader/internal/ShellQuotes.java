package io.github.fiftieshousewife.dockerfile.reader.internal;

final class ShellQuotes {

    private static final String ESCAPED_IN_DOUBLE_QUOTES = "\"\\$`";

    private ShellQuotes() {
    }

    static void escaped(ShellScan scan) {
        if (scan.at + 1 < scan.script.length() && scan.next() != '\n') {
            scan.word.append(literal(scan.next()));
        }
        scan.at++;
    }

    static void singleQuoted(ShellScan scan) {
        int close = scan.script.indexOf('\'', scan.at + 1);
        if (close < 0) {
            scan.unreadable = true;
            return;
        }
        scan.word.append(scan.script.substring(scan.at + 1, close).replace('$', ShellToken.LITERAL_DOLLAR));
        scan.quoted = true;
        scan.at = close;
    }

    static void doubleQuoted(ShellScan scan) {
        for (scan.at++; scan.more(); scan.at++) {
            char c = scan.script.charAt(scan.at);
            if (c == '"') {
                scan.quoted = true;
                return;
            }
            switch (c) {
                case '\\' -> escapedInDoubleQuotes(scan);
                case '$' -> ShellSubstitutions.dollar(scan);
                case '`' -> ShellSubstitutions.backticks(scan);
                default -> scan.word.append(c);
            }
        }
        scan.unreadable = true;
    }

    static void escapedInDoubleQuotes(ShellScan scan) {
        if (scan.at + 1 == scan.script.length()) {
            scan.unreadable = true;
        } else if (scan.next() == '\n') {
            scan.at++;
        } else if (ESCAPED_IN_DOUBLE_QUOTES.indexOf(scan.next()) >= 0) {
            scan.word.append(literal(scan.next()));
            scan.at++;
        } else {
            scan.word.append('\\');
        }
    }

    static char literal(char c) {
        return c == '$' ? ShellToken.LITERAL_DOLLAR : c;
    }
}
