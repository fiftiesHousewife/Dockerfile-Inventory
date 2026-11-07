package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.openrewrite.Cursor;
import org.openrewrite.docker.tree.Docker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The final image's instructions as {@link Step}s, in the form Buildah records them, so lineage can match an image's
 * history to the Dockerfile that built it.
 */
public final class Steps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FLAGS = Pattern.compile("^(--\\S+\\s+)*");

    private Steps() {
    }

    /** The final image's instructions, {@code ARG} excepted, each joined onto one line as Buildah records it. */
    public static List<Step> of(Docker.File file) {
        return finalImage(file).stream()
                .filter(instruction -> !(instruction instanceof Docker.Arg))
                .map(instruction -> Continuations.oneLine(instruction.printTrimmed(new Cursor(null, file))))
                .map(Steps::fromLine)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // A final stage built FROM an earlier stage by name starts with that stage's layers and history. As in Docker, a
    // name refers only to an earlier stage, so FROM app AS app names an image, not itself.
    static List<Docker.Instruction> finalImage(Docker.File file) {
        List<Docker.Stage> stages = file.getStages();
        List<Docker.Instruction> instructions = new ArrayList<>();
        int index = stages.size() - 1;
        while (index >= 0) {
            Docker.Stage stage = stages.get(index);
            instructions.addAll(0, stage.getInstructions());
            index = earlierStage(stage.getFrom(), stages.subList(0, index));
        }
        return instructions;
    }

    static int earlierStage(Docker.From from, List<Docker.Stage> earlier) {
        if (from.getTag() != null || from.getDigest() != null) {
            return -1;
        }
        String name = from.getImageName().getContents().stream()
                .map(c -> c instanceof Docker.Literal l ? l.getText() : "$")
                .collect(Collectors.joining());
        return IntStream.iterate(earlier.size() - 1, i -> i >= 0, i -> i - 1)
                .filter(i -> named(earlier.get(i), name))
                .findFirst().orElse(-1);
    }

    static boolean named(Docker.Stage stage, String name) {
        Docker.From.As as = stage.getFrom().getAs();
        return as != null && as.getName().getText().equals(name);
    }

    static Step fromLine(String line) {
        int space = line.indexOf(' ');
        String keyword = (space < 0 ? line : line.substring(0, space)).toUpperCase(Locale.ROOT);
        return step(keyword, space < 0 ? "" : line.substring(space + 1));
    }

    static Step step(String keyword, String value) {
        return switch (keyword) {
            case "COPY", "ADD" -> new Step(keyword, destination(value));
            case "LABEL", "ENV" -> new Step(keyword, pairKeys(value));
            default -> new Step(keyword, value);
        };
    }

    // Buildah records only a COPY's or ADD's destination.
    static String destination(String value) {
        String arguments = FLAGS.matcher(value).replaceFirst("");
        if (arguments.startsWith("[")) {
            JsonNode array = JSON.readTree(arguments);
            return array.get(array.size() - 1).asString();
        }
        return arguments.substring(arguments.lastIndexOf(' ') + 1);
    }

    static String pairKeys(String value) {
        String first = value.split(" ", 2)[0];
        return first.contains("=") ? String.join(" ", KeyValuePairs.keys(value)) : first.replace("\"", "");
    }
}
