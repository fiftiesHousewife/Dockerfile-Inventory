package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Each rewrite works around a rewrite-docker bug (docs/upstream/) and keeps every line where it was.
final class RetryText {

    private static final Pattern HEREDOC = Pattern.compile("<<-?['\"]?(\\w+)");
    private static final Pattern FLAG_DEFAULT = Pattern.compile("(--[A-Za-z-]+=\\S*?\\$\\{[A-Za-z_][A-Za-z0-9_]*):-");
    private static final Pattern ARG_LINE = Pattern.compile("(?i)(\\s*ARG\\s+)?\\s*(\\S.*?)(\\s*\\\\)?\\s*");
    private static final Pattern SIMPLE_ARG_WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(=[^\"'\\\\\\s]*)?");

    private RetryText() {
    }

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
            String line = flagDefaults(lines.get(i));
            Matcher arg = ARG_LINE.matcher(line);
            boolean argLine = arg.matches() && (inArg || arg.group(1) != null);
            rewritten.add(argLine ? argNames(arg) : line);
            inArg = argLine && arg.group(3) != null;
        }
        return withoutComments(String.join("\n", rewritten) + (text.endsWith("\n") ? "\n" : ""));
    }

    // rewrite-docker issue 9 (docs/upstream/); ${NAME:-x} and ${NAME-x} differ only for a set but empty variable.
    static String flagDefaults(String line) {
        return FLAG_DEFAULT.matcher(line).replaceAll("$1-");
    }

    // rewrite-docker issues 10 and 13 (docs/upstream/)
    static String argNames(Matcher arg) {
        String[] words = arg.group(2).trim().split("\\s+");
        if (!Arrays.stream(words).allMatch(w -> SIMPLE_ARG_WORD.matcher(w).matches())) {
            return arg.group();
        }
        return arg.group().substring(0, arg.start(2)) + Arrays.stream(words).map(RetryText::argWord)
                .collect(Collectors.joining(" ")) + (arg.group(3) == null ? "" : arg.group(3));
    }

    static String argWord(String word) {
        return word.endsWith("=") ? word + "\"\"" : word.contains("=") ? word : word + "=$" + word;
    }

    // rewrite-docker issues 1, 2 and 7 (docs/upstream/): inside a continued instruction a comment becomes a lone
    // backslash, which keeps the instruction going as Docker does. A lone backslash outside one, from which Docker
    // reads nothing, is blanked, as rewrite-docker glues it onto the instruction before.
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

    static boolean[] heredocBodies(List<String> lines) {
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
