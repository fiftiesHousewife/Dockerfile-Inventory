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

    /** One line, as Buildah records it: backslash-newline continuations joined and comment lines inside dropped. */
    static String joined(String instruction) {
        List<String> kept = instruction.lines()
                .map(line -> line.endsWith("\\") ? line.substring(0, line.length() - 1) : line)
                .filter(line -> !line.strip().startsWith("#"))
                .toList();
        return WHITESPACE.matcher(String.join(" ", kept)).replaceAll(" ").strip();
    }
}
