package io.github.fiftieshousewife.dockerfileinventory.facts;

final class ShellSubstitutions {

    private ShellSubstitutions() {
    }

    static void dollar(ShellScan scan) {
        if (scan.next() == '(') {
            scan.appendThrough(end(scan.script, scan.at));
        } else {
            scan.word.append('$');
        }
    }

    static void backticks(ShellScan scan) {
        scan.appendThrough(scan.script.indexOf('`', scan.at + 1));
    }

    static int end(String script, int dollar) {
        int depth = 0;
        for (int i = dollar + 1; i < script.length(); i++) {
            char c = script.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '\'' || c == '"') {
                int close = script.indexOf(c, i + 1);
                if (close < 0) {
                    return -1;
                }
                i = close;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
