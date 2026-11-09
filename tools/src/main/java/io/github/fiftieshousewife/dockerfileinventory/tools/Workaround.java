package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

final class Workaround {

    private record Sign(String kind, Pattern line) {
    }

    private static final List<Sign> SIGNS = List.of(
            new Sign("json-comma", Pattern.compile("\\[.*\"\\s+,")),
            new Sign("flag-default", Pattern.compile("--[A-Za-z-]+=\\S*\\$\\{[A-Za-z_][A-Za-z0-9_]*:-")),
            new Sign("arg-empty", Pattern.compile("^\\s*ARG\\s.*[A-Za-z_][A-Za-z0-9_]*=(\\s|$)")),
            new Sign("arg-names", Pattern.compile("^\\s*ARG\\s+[A-Za-z_][A-Za-z0-9_]*(\\s+[A-Za-z_][A-Za-z0-9_=]*)+")),
            new Sign("lone-backslash", Pattern.compile("^\\s*\\\\\\s*$")));
    private static final Pattern CONTINUED = Pattern.compile("\\\\\\s*$");
    private static final Pattern COMMENT = Pattern.compile("^\\s*#");
    private static final Pattern KEY_VALUE_COMMENT = Pattern.compile("^\\s*#\\s*[A-Za-z_]+\\s*=");
    private static final Pattern INSTRUCTION = Pattern.compile("^\\s*[A-Za-z]");

    private Workaround() {
    }

    static String of(String text) {
        List<String> lines = text.lines().toList();
        return SIGNS.stream().filter(sign -> lines.stream().anyMatch(line -> sign.line().matcher(line).find()))
                .map(Sign::kind).findFirst().orElseGet(() -> multiLine(lines));
    }

    static String multiLine(List<String> lines) {
        if (commentInContinuation(lines)) {
            return "comment-in-continuation";
        }
        return keyValueCommentAfterAnInstruction(lines) ? "key-value-comment" : "other";
    }

    static boolean commentInContinuation(List<String> lines) {
        return IntStream.range(1, lines.size()).anyMatch(i -> CONTINUED.matcher(lines.get(i - 1)).find()
                && COMMENT.matcher(lines.get(i)).find());
    }

    static boolean keyValueCommentAfterAnInstruction(List<String> lines) {
        return lines.stream().dropWhile(line -> !INSTRUCTION.matcher(line).find()).skip(1)
                .anyMatch(line -> KEY_VALUE_COMMENT.matcher(line).find());
    }
}
