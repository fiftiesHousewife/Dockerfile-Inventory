package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
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
                .map(Convert::arg)
                .toList();
        Set<String> earlierStages = new HashSet<>();
        List<Stage> stages = new ArrayList<>();
        List<List<List<String>>> comments = new ArrayList<>();
        for (Docker.Stage stage : file.getStages()) {
            Docker.From from = stage.getFrom();
            Docker.From.As as = from.getAs();
            Docker.Argument tag = from.getTag();
            Docker.Argument digest = from.getDigest();
            String name = as == null ? null : as.getName().getText();
            String image = text(from.getImageName());
            boolean onStage = tag == null && digest == null && earlierStages.contains(image);
            BaseImage base = onStage ? null : new BaseImage(image, tag == null ? null : text(tag),
                    digest == null ? null : text(digest));
            List<Instruction> instructions = stage.getInstructions().stream().map(this::instruction).toList();
            comments.add(stage.getInstructions().stream().map(this::comments).toList());
            stages.add(new Stage(name, base, onStage ? image : null, instructions));
            if (name != null) {
                earlierStages.add(name);
            }
        }
        return new Conversion(new Dockerfile(globalArgs, stages), comments);
    }

    private Instruction instruction(Docker.Instruction instruction) {
        return switch (instruction) {
            case Docker.Arg arg -> arg(arg);
            case Docker.Env env -> new Instruction.Env(env(env));
            case Docker.Label label -> new Instruction.Label(labels(afterKeyword(printed(label))));
            case Docker.Workdir workdir -> new Instruction.Workdir(unquote(text(workdir.getPath())));
            case Docker.User user -> user(user);
            case Docker.Expose expose when single(expose) ->
                    new Instruction.Expose(Integer.parseInt(expose.getPorts().getFirst().getText().strip()));
            case Docker.Run run -> run(run);
            case Docker.Entrypoint entrypoint when entrypoint.getCommand() instanceof Docker.ExecForm exec ->
                    new Instruction.Entrypoint(arguments(exec));
            case Docker.Cmd cmd when cmd.getCommand() instanceof Docker.ExecForm exec ->
                    new Instruction.Cmd(arguments(exec));
            case Docker.Copy copy -> copy(printed(copy));
            case Docker.Entrypoint entrypoint -> new Instruction.Raw(printed(entrypoint), "shell-form ENTRYPOINT");
            case Docker.Cmd cmd -> new Instruction.Raw(printed(cmd), "shell-form CMD");
            case Docker.Expose expose -> new Instruction.Raw(printed(expose),
                    "EXPOSE with a protocol, range, variable or several ports");
            default -> {
                String text = printed(instruction);
                String keyword = text.split("\\s", 2)[0].toUpperCase(java.util.Locale.ROOT);
                yield new Instruction.Raw(text, keyword + " is not modelled");
            }
        };
    }

    private static Instruction.Arg arg(Docker.Arg arg) {
        Docker.Argument value = arg.getValue();
        return new Instruction.Arg(arg.getName().getText(), value == null ? null : unquote(text(value)));
    }

    private static Instruction.User user(Docker.User user) {
        Docker.Argument group = user.getGroup();
        return new Instruction.User(text(user.getUser()) + (group == null ? "" : ":" + text(group)));
    }

    /** An install when it is exactly one install and its clean; other shell text as written; the rest raw (D1, D2). */
    private Instruction run(Docker.Run run) {
        List<Docker.Flag> flags = run.getFlags();
        if (flags != null && !flags.isEmpty()) {
            return new Instruction.Raw(printed(run), "RUN with flags");
        }
        if (!(run.getCommand() instanceof Docker.ShellForm shell)) {
            return new Instruction.Raw(printed(run), "RUN in exec or heredoc form");
        }
        String command = joined(shell.getArgument().getText());
        return Installs.of(command)
                .<Instruction>map(install -> new Instruction.Run(new RunBody.Install(install)))
                .orElseGet(() -> new Instruction.Run(new RunBody.Shell(List.of(command))));
    }

    /** Docker removes a backslash-newline before it reads an instruction, inside quotes too. */
    static String withoutContinuations(String text) {
        return text.replaceAll("\\\\\\r?\\n", "");
    }

    /** One line: continuations joined, comment lines inside dropped (Docker drops them), whitespace collapsed. */
    static String joined(String text) {
        return text.lines()
                .map(line -> line.endsWith("\\") ? line.substring(0, line.length() - 1) : line)
                .filter(line -> !line.strip().startsWith("#"))
                .collect(Collectors.joining(" "))
                .replaceAll("\\s+", " ")
                .strip();
    }

    /** {@code COPY [--from=x] [--chown=y] src... dest}; other flags and the JSON form stay raw (D2). */
    private static Instruction copy(String text) {
        List<String> words = new ArrayList<>(List.of(joined(text).split(" ")));
        words.removeFirst();
        String from = null;
        String chown = null;
        while (!words.isEmpty() && words.getFirst().startsWith("--")) {
            String flag = words.removeFirst();
            if (flag.startsWith("--from=")) {
                from = flag.substring("--from=".length());
            } else if (flag.startsWith("--chown=")) {
                chown = flag.substring("--chown=".length());
            } else {
                return new Instruction.Raw(text, "COPY with flag " + flag.split("=", 2)[0]);
            }
        }
        if (words.isEmpty() || words.getFirst().startsWith("[")) {
            return new Instruction.Raw(text, "COPY in JSON form");
        }
        if (words.size() < 2) {
            return new Instruction.Raw(text, "COPY without a source and a destination");
        }
        return new Instruction.Copy(words.subList(0, words.size() - 1), words.getLast(), chown, from);
    }

    /** rewrite-docker gives an empty {@code KEY=} the value {@code =}, and keeps a value's quotes in its text. */
    private static Map<String, String> env(Docker.Env env) {
        Map<String, String> values = new LinkedHashMap<>();
        env.getPairs().forEach(pair -> {
            Docker.Argument argument = pair.getValue();
            String value = argument == null ? "" : withoutContinuations(text(argument));
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

    /** The instruction's arguments: comment lines inside it dropped, continuations removed, keyword split off. */
    private static String afterKeyword(String instruction) {
        String text = instruction.lines()
                .filter(line -> !line.strip().startsWith("#"))
                .collect(Collectors.joining("\n"));
        String[] parts = withoutContinuations(text).strip().split("\\s+", 2);
        return parts.length < 2 ? "" : parts[1];
    }

    /** {@code LABEL k=v ...}, or the legacy {@code LABEL key value with spaces}, which sets one label. */
    private static Map<String, String> labels(String arguments) {
        String first = arguments.split("\\s+", 2)[0];
        if (first.contains("=")) {
            return Pairs.parse(arguments);
        }
        String value = arguments.substring(first.length()).strip();
        return Map.of(unquote(first), unquote(value));
    }

    /** The comment lines the printer puts before an instruction, without their {@code #}. */
    private List<String> comments(Docker.Instruction instruction) {
        List<String> comments = new ArrayList<>();
        for (String line : instruction.printTrimmed(cursor).lines().toList()) {
            String stripped = line.strip();
            if (!stripped.startsWith("#")) {
                break;
            }
            comments.add(stripped.substring(1).strip());
        }
        return comments;
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
