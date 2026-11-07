package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Docker's line continuations, read in one place so facts, rules and the converter join lines the same way Docker
 * does.
 */
public final class Continuations {

    private static final Pattern CONTINUATION = Pattern.compile("\\\\[ \\t]*\\r?\\n");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Continuations() {
    }

    /** Each backslash-newline removed with nothing in its place, as Docker removes it. */
    public static String removed(String text) {
        return CONTINUATION.matcher(text).replaceAll("");
    }

    /**
     * The instruction on one line: continued lines joined, comment lines inside dropped as Docker drops them,
     * whitespace collapsed. A backslash ending the last line joins nothing, so it stays ({@code C:\\Windows\\}).
     */
    public static String oneLine(String instruction) {
        List<String> lines = instruction.stripTrailing().lines().map(String::stripTrailing).toList();
        String joined = IntStream.range(0, lines.size())
                .mapToObj(i -> i < lines.size() - 1 ? withoutTrailingBackslash(lines.get(i)) : lines.get(i))
                .filter(line -> !line.strip().startsWith("#"))
                .collect(Collectors.joining(" "));
        return WHITESPACE.matcher(joined).replaceAll(" ").strip();
    }

    /** The text without its whole-line comments, which Docker drops before the shell reads the rest, quotes or not. */
    public static String withoutCommentLines(String text) {
        return text.lines().filter(line -> !line.strip().startsWith("#")).collect(Collectors.joining("\n"));
    }

    static String withoutTrailingBackslash(String line) {
        return line.endsWith("\\") ? line.substring(0, line.length() - 1) : line;
    }
}
