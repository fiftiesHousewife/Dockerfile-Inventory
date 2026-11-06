package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The text of the second parse, which runs only after rewrite-docker rejects a file: each known parser bug that a
 * rewrite can avoid is rewritten, keeping every line where it was and the file's meaning (docs/upstream/). Heredoc
 * bodies are the script's own lines and are never touched.
 */
final class RetryText {

    private static final Pattern HEREDOC = Pattern.compile("<<-?['\"]?(\\w+)");
    private static final Pattern SPACE_BEFORE_COMMA = Pattern.compile("\"[ \\t]+,");
    private static final Pattern FLAG_DEFAULT = Pattern.compile("(--[A-Za-z-]+=\\S*?\\$\\{[A-Za-z_][A-Za-z0-9_]*):-");
    /** A line of an {@code ARG}: its keyword (absent on a continued line), its words, a trailing backslash. */
    private static final Pattern ARG_LINE = Pattern.compile("(?i)(\\s*ARG\\s+)?\\s*(\\S.*?)(\\s*\\\\)?\\s*");
    /** A plain name, or a name with a value that holds no quote, backslash or space. */
    private static final Pattern SIMPLE_ARG_WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(=[^\"'\\\\\\s]*)?");

    private RetryText() {
    }

    /** The whole text, every rewrite applied, then whole-line comments blanked. */
    static String of(String text) {
        List<String> lines = text.lines().toList();
        boolean[] body = heredocBodies(lines);
        List<String> rewritten = new ArrayList<>();
        boolean inArg = false;
        for (int i = 0; i < lines.size(); i++) {
            if (body[i]) {
                rewritten.add(lines.get(i));
                continue;
            }
            String line = flagDefaults(jsonCommas(lines.get(i)));
            Matcher arg = ARG_LINE.matcher(line);
            boolean argLine = arg.matches() && (inArg || arg.group(1) != null);
            rewritten.add(argLine ? argNames(arg) : line);
            inArg = argLine && arg.group(3) != null;
        }
        return withoutComments(String.join("\n", rewritten) + (text.endsWith("\n") ? "\n" : ""));
    }

    /** Whitespace before a comma in a JSON array dropped ({@code CMD ["a" , "b"]}, upstream issue 8). */
    static String jsonCommas(String line) {
        return line.contains("[") ? SPACE_BEFORE_COMMA.matcher(line).replaceAll("\",") : line;
    }

    /**
     * In a flag's value, {@code ${NAME:-default}} written {@code ${NAME-default}} (upstream issue 9); the two differ
     * only when the variable is set but empty.
     */
    static String flagDefaults(String line) {
        return FLAG_DEFAULT.matcher(line).replaceAll("$1-");
    }

    /**
     * One line of an {@code ARG}, its first or a continued one: when every word is a plain name or a simple
     * {@code NAME=value}, each plain name is given {@code =$NAME} (upstream issue 10, which leaves it unknown, as
     * before) and an empty default is written {@code NAME=""} (issue 13); the trailing backslash is kept.
     */
    private static String argNames(Matcher arg) {
        String[] words = arg.group(2).trim().split("\\s+");
        if (!Arrays.stream(words).allMatch(w -> SIMPLE_ARG_WORD.matcher(w).matches())) {
            return arg.group();
        }
        return arg.group().substring(0, arg.start(2)) + Arrays.stream(words).map(RetryText::argWord)
                .collect(Collectors.joining(" ")) + (arg.group(3) == null ? "" : arg.group(3));
    }

    private static String argWord(String word) {
        return word.endsWith("=") ? word + "\"\"" : word.contains("=") ? word : word + "=$" + word;
    }

    /**
     * Whole-line comments blanked, keeping the line count (upstream issues 1, 2 and 7): inside a continued
     * instruction a comment becomes a lone backslash, which keeps the instruction going as Docker does, and elsewhere
     * an empty line. The leading comment block (where parser directives such as {@code # syntax=} live) and heredoc
     * bodies are left as they are. A line holding only a backslash outside an instruction is blanked too: Docker reads
     * nothing from it, but rewrite-docker glues it onto the instruction before.
     */
    static String withoutComments(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        boolean[] body = heredocBodies(lines);
        boolean leading = true;
        boolean continued = false;
        for (int i = 0; i < lines.size(); i++) {
            String stripped = lines.get(i).strip();
            if (body[i] || stripped.startsWith("#") && leading) {
                continue;
            }
            if (stripped.startsWith("#") || stripped.equals("\\") && !continued) {
                lines.set(i, stripped.startsWith("#") && continued ? "\\" : "");
                continue;
            }
            leading = leading && stripped.isEmpty();
            continued = stripped.endsWith("\\");
        }
        return String.join("\n", lines);
    }

    /** Which lines are inside a heredoc body, its closing word included. A comment line opens none. */
    private static boolean[] heredocBodies(List<String> lines) {
        boolean[] body = new boolean[lines.size()];
        String end = null;
        for (int i = 0; i < lines.size(); i++) {
            if (end != null) {
                body[i] = true;
                end = lines.get(i).strip().equals(end) ? null : end;
            } else if (!lines.get(i).strip().startsWith("#")) {
                Matcher opens = HEREDOC.matcher(lines.get(i));
                end = opens.find() ? opens.group(1) : null;
            }
        }
        return body;
    }
}
