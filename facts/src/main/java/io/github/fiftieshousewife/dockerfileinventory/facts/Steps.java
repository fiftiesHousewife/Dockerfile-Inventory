package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.openrewrite.Cursor;
import org.openrewrite.docker.tree.Docker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The final image's instructions as {@link Step}s, matching what Buildah records: continuations joined, comment
 * lines inside an instruction dropped, whitespace collapsed. With {@link FactsParser} and {@link FactExtractor},
 * the only classes here that see rewrite types.
 */
public final class Steps {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FLAGS = Pattern.compile("^(--\\S+\\s+)*");

    private Steps() {
    }

    public static List<Step> of(Docker.File file) {
        List<Step> steps = new ArrayList<>();
        Docker.Stage last = file.getStages().getLast();
        for (Docker.Instruction instruction : last.getInstructions()) {
            String text = joined(instruction.printTrimmed(new Cursor(null, file)));
            int space = text.indexOf(' ');
            String keyword = (space < 0 ? text : text.substring(0, space)).toUpperCase(Locale.ROOT);
            steps.add(step(keyword, space < 0 ? "" : text.substring(space + 1)));
        }
        return steps;
    }

    static Step step(String keyword, String value) {
        return switch (keyword) {
            case "COPY", "ADD" -> new Step(keyword, destination(value));
            case "LABEL" -> new Step(keyword, String.join(" ", keys(value)));
            case "ENV" -> new Step(keyword, envKeys(value));
            default -> new Step(keyword, value);
        };
    }

    /** The last argument, as Buildah records only the destination; in exec form, the array's last element. */
    static String destination(String value) {
        String arguments = FLAGS.matcher(value).replaceFirst("");
        if (arguments.startsWith("[")) {
            JsonNode array = JSON.readTree(arguments);
            return array.get(array.size() - 1).asString();
        }
        return arguments.substring(arguments.lastIndexOf(' ') + 1);
    }

    /** {@code ENV k=v ...}, or the legacy {@code ENV k value with spaces}, which sets one variable. */
    static String envKeys(String value) {
        String first = value.split(" ", 2)[0];
        return first.contains("=") ? String.join(" ", keys(value)) : first.replace("\"", "");
    }

    /**
     * The keys of {@code key=value} pairs, sorted. Words split on whitespace outside double quotes; quotes are removed
     * and a backslash escapes the next character. The same rule as lineage's {@code HistoryNormaliser}; the contract
     * in {@code testdata/contracts/} holds the two together.
     */
    static List<String> keys(String pairs) {
        List<String> keys = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        boolean inKey = true;
        boolean quoted = false;
        boolean escaped = false;
        for (char c : pairs.toCharArray()) {
            if (escaped) {
                escaped = false;
                if (inKey) {
                    key.append(c);
                }
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && Character.isWhitespace(c)) {
                addIfAny(keys, key);
                inKey = true;
            } else if (!quoted && c == '=' && inKey) {
                inKey = false;
            } else if (inKey) {
                key.append(c);
            }
        }
        addIfAny(keys, key);
        return keys.stream().sorted().toList();
    }

    private static void addIfAny(List<String> keys, StringBuilder key) {
        if (!key.isEmpty()) {
            keys.add(key.toString());
            key.setLength(0);
        }
    }

    /** One line, as Buildah records it: backslash-newline continuations joined and comment lines inside dropped. */
    static String joined(String instruction) {
        List<String> kept = instruction.lines()
                .map(line -> line.endsWith("\\") ? line.substring(0, line.length() - 1) : line)
                .filter(line -> !line.strip().startsWith("#"))
                .toList();
        return WHITESPACE.matcher(String.join(" ", kept)).replaceAll(" ").strip();
    }
}
