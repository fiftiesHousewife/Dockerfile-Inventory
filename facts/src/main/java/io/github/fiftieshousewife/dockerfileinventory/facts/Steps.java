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
 * The final image's instructions as {@link Step}s, matching what Buildah records: continuations joined, comment
 * lines inside an instruction dropped, whitespace collapsed. With {@link FactsParser} and {@link FactExtractor},
 * the only classes here that see rewrite types.
 */
public final class Steps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FLAGS = Pattern.compile("^(--\\S+\\s+)*");

    private Steps() {
    }

    public static List<Step> of(Docker.File file) {
        List<Step> steps = new ArrayList<>();
        for (Docker.Instruction instruction : finalImage(file)) {
            if (instruction instanceof Docker.Arg) {
                continue;
            }
            String text = Continuations.oneLine(instruction.printTrimmed(new Cursor(null, file)));
            int space = text.indexOf(' ');
            String keyword = (space < 0 ? text : text.substring(0, space)).toUpperCase(Locale.ROOT);
            steps.add(step(keyword, space < 0 ? "" : text.substring(space + 1)));
        }
        return steps;
    }

    /**
     * The final stage's instructions, preceded by those of each earlier stage it is built {@code FROM} by name:
     * their layers and history are what the final image starts with. As in Docker, a name refers only to a stage
     * before the one that uses it, so {@code FROM app AS app} names an image, not itself.
     */
    private static List<Docker.Instruction> finalImage(Docker.File file) {
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

    /** The index of the last earlier stage named as this one's base, or -1 when the base is an image. */
    private static int earlierStage(Docker.From from, List<Docker.Stage> earlier) {
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

    private static boolean named(Docker.Stage stage, String name) {
        Docker.From.As as = stage.getFrom().getAs();
        return as != null && as.getName().getText().equals(name);
    }

    static Step step(String keyword, String value) {
        return switch (keyword) {
            case "COPY", "ADD" -> new Step(keyword, destination(value));
            case "LABEL", "ENV" -> new Step(keyword, pairKeys(value));
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

    /** {@code ENV} or {@code LABEL} {@code k=v ...}, or the legacy {@code k value with spaces}, which sets one. */
    static String pairKeys(String value) {
        String first = value.split(" ", 2)[0];
        return first.contains("=") ? String.join(" ", KeyValuePairs.keys(value)) : first.replace("\"", "");
    }

}
