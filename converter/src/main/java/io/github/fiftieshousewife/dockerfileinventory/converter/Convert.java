package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Stage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.openrewrite.Cursor;
import org.openrewrite.docker.tree.Docker;

/**
 * Maps a parsed Dockerfile to the Phase 4 model, faithfully (Phase 5, Q1): what the model types is typed, and
 * everything else is kept as {@link Instruction.Raw} with its reason (D2).
 */
public final class Convert {

    private final Docker.File file;
    private final Cursor cursor;

    private Convert(Docker.File file) {
        this.file = file;
        this.cursor = new Cursor(null, file);
    }

    public static Conversion of(Docker.File file) {
        return new Convert(file).convert();
    }

    private Conversion convert() {
        List<Instruction.Arg> globalArgs = file.getGlobalArgs().stream()
                .map(arg -> new Instruction.Arg(arg.getName().getText(),
                        arg.getValue() == null ? null : unquote(text(arg.getValue()))))
                .toList();
        Set<String> earlierStages = new HashSet<>();
        List<Stage> stages = new ArrayList<>();
        for (Docker.Stage stage : file.getStages()) {
            Docker.From from = stage.getFrom();
            String name = from.getAs() == null ? null : from.getAs().getName().getText();
            String image = text(from.getImageName());
            boolean onStage = from.getTag() == null && from.getDigest() == null && earlierStages.contains(image);
            BaseImage base = onStage ? null : new BaseImage(image,
                    from.getTag() == null ? null : text(from.getTag()),
                    from.getDigest() == null ? null : text(from.getDigest()));
            List<Instruction> instructions = stage.getInstructions().stream().map(this::instruction).toList();
            stages.add(new Stage(name, base, onStage ? image : null, instructions));
            if (name != null) {
                earlierStages.add(name);
            }
        }
        return new Conversion(new Dockerfile(globalArgs, stages));
    }

    private Instruction instruction(Docker.Instruction instruction) {
        return switch (instruction) {
            case Docker.Arg arg -> new Instruction.Arg(arg.getName().getText(),
                    arg.getValue() == null ? null : unquote(text(arg.getValue())));
            case Docker.Env env -> new Instruction.Env(env(env));
            case Docker.Label label -> new Instruction.Label(Pairs.parse(afterKeyword(printed(label))));
            case Docker.Workdir workdir -> new Instruction.Workdir(unquote(text(workdir.getPath())));
            case Docker.User user -> new Instruction.User(text(user.getUser())
                    + (user.getGroup() == null ? "" : ":" + text(user.getGroup())));
            case Docker.Expose expose when single(expose) ->
                    new Instruction.Expose(Integer.parseInt(expose.getPorts().getFirst().getText().strip()));
            case Docker.Entrypoint entrypoint when entrypoint.getCommand() instanceof Docker.ExecForm exec ->
                    new Instruction.Entrypoint(arguments(exec));
            case Docker.Cmd cmd when cmd.getCommand() instanceof Docker.ExecForm exec ->
                    new Instruction.Cmd(arguments(exec));
            default -> new Instruction.Raw(printed(instruction), "not converted yet");
        };
    }

    /** rewrite-docker gives an empty {@code KEY=} the value {@code =}, and keeps a value's quotes in its text. */
    private static Map<String, String> env(Docker.Env env) {
        Map<String, String> values = new LinkedHashMap<>();
        env.getPairs().forEach(pair -> {
            String value = pair.getValue() == null ? "" : text(pair.getValue());
            values.put(pair.getKey().getText(), value.equals("=") ? "" : unquote(value));
        });
        return values;
    }

    /** One port written as plain digits: no protocol, range or variable; anything else stays {@code Raw} (D2). */
    private static boolean single(Docker.Expose expose) {
        List<Docker.Port> ports = expose.getPorts();
        return ports.size() == 1 && ports.getFirst().getText().strip().matches("\\d{1,5}");
    }

    private static List<String> arguments(Docker.ExecForm exec) {
        return exec.getArguments().stream().map(l -> unquote(l.getText())).toList();
    }

    private static String afterKeyword(String instruction) {
        String joined = instruction.replaceAll("\\\\\\r?\\n", " ").strip();
        int space = joined.indexOf(' ');
        return space < 0 ? "" : joined.substring(space + 1);
    }

    /** The instruction as written, without the comment lines the printer puts before it. */
    String printed(Docker.Instruction instruction) {
        String text = instruction.printTrimmed(cursor).stripLeading();
        while (text.startsWith("#")) {
            int newline = text.indexOf('\n');
            text = newline < 0 ? "" : text.substring(newline + 1).stripLeading();
        }
        return text;
    }

    /** An argument's text, with a variable as {@code ${NAME}}. */
    static String text(Docker.Argument argument) {
        return argument.getContents().stream()
                .map(c -> c instanceof Docker.Literal l ? l.getText()
                        : "${" + ((Docker.EnvironmentVariable) c).getName() + "}")
                .collect(Collectors.joining());
    }

    /** A value without its surrounding double or single quotes, escapes inside double quotes undone. */
    static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
