package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// The parser workaround a file that parsed only on the retry needs, told by the first of the signs its text shows.
final class Workaround {

    private static final Map<String, Pattern> BY_LINE = Map.of(
            "json-comma", Pattern.compile("\\[.*\"\\s+,"),
            "flag-default", Pattern.compile("--[A-Za-z-]+=\\S*\\$\\{[A-Za-z_][A-Za-z0-9_]*:-"),
            "arg-empty", Pattern.compile("^\\s*ARG\\s.*[A-Za-z_][A-Za-z0-9_]*=(\\s|$)"),
            "arg-names", Pattern.compile("^\\s*ARG\\s+[A-Za-z_][A-Za-z0-9_]*(\\s+[A-Za-z_][A-Za-z0-9_=]*)+"),
            "lone-backslash", Pattern.compile("^\\s*\\\\\\s*$"));
    private static final List<String> ORDER = List.of("json-comma", "flag-default", "arg-empty", "arg-names",
            "lone-backslash");
    private static final Pattern CONTINUED = Pattern.compile("\\\\\\s*$");
    private static final Pattern COMMENT = Pattern.compile("^\\s*#");
    private static final Pattern KEY_VALUE_COMMENT = Pattern.compile("^\\s*#\\s*[A-Za-z_]+\\s*=");
    private static final Pattern INSTRUCTION = Pattern.compile("^\\s*[A-Za-z]");

    private Workaround() {
    }

    static String of(String text) {
        List<String> lines = text.lines().toList();
        for (String kind : ORDER) {
            if (lines.stream().anyMatch(line -> BY_LINE.get(kind).matcher(line).find())) {
                return kind;
            }
        }
        if (commentInContinuation(lines)) {
            return "comment-in-continuation";
        }
        return keyValueComment(lines) ? "key-value-comment" : "other";
    }

    static boolean commentInContinuation(List<String> lines) {
        for (int i = 1; i < lines.size(); i++) {
            if (CONTINUED.matcher(lines.get(i - 1)).find() && COMMENT.matcher(lines.get(i)).find()) {
                return true;
            }
        }
        return false;
    }

    // A KEY = value comment after the first instruction.
    static boolean keyValueComment(List<String> lines) {
        boolean seen = false;
        for (String line : lines) {
            if (seen && KEY_VALUE_COMMENT.matcher(line).find()) {
                return true;
            }
            seen = seen || INSTRUCTION.matcher(line).find();
        }
        return false;
    }
}
