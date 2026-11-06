package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Stage;
import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactExtractor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
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

    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();
    private static final String NOT_JSON = "exec form that is not valid JSON";
    /** A heredoc opened on an instruction's first line ({@code <<EOF}, {@code <<-'EOF'}); its body is kept. */
    private static final java.util.regex.Pattern HEREDOC = java.util.regex.Pattern.compile("<<-?['\"]?\\w+");
    private static final java.util.regex.Pattern BARE_VARIABLE =
            java.util.regex.Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

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
                .flatMap(arg -> args(arg).stream())
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
            List<Instruction> instructions = new ArrayList<>();
            List<List<String>> stageComments = new ArrayList<>();
            for (Docker.Instruction instruction : stage.getInstructions()) {
                List<Instruction> converted = instruction instanceof Docker.Arg arg
                        ? List.copyOf(args(arg)) : List.of(instruction(instruction));
                instructions.addAll(converted);
                stageComments.add(comments(instruction));
                converted.stream().skip(1).forEach(extra -> stageComments.add(List.of()));
            }
            comments.add(stageComments);
            stages.add(new Stage(name, base, onStage ? image : null, instructions));
            if (name != null) {
                earlierStages.add(name);
            }
        }
        return new Conversion(new Dockerfile(globalArgs, stages), comments);
    }

    private Instruction instruction(Docker.Instruction instruction) {
        return switch (instruction) {
            case Docker.Env env -> new Instruction.Env(pairs(afterKeyword(printed(env))));
            case Docker.Label label -> new Instruction.Label(pairs(afterKeyword(printed(label))));
            case Docker.Workdir workdir -> new Instruction.Workdir(unquote(text(workdir.getPath())));
            case Docker.User user -> user(user);
            case Docker.Expose expose when single(expose) ->
                    new Instruction.Expose(Integer.parseInt(expose.getPorts().getFirst().getText().strip()));
            case Docker.Run run -> run(run);
            case Docker.Entrypoint entrypoint when entrypoint.getCommand() instanceof Docker.ExecForm exec ->
                    arguments(exec).<Instruction>map(Instruction.Entrypoint::new)
                            .orElseGet(() -> new Instruction.Raw(printed(entrypoint), NOT_JSON));
            case Docker.Cmd cmd when cmd.getCommand() instanceof Docker.ExecForm exec ->
                    arguments(exec).<Instruction>map(Instruction.Cmd::new)
                            .orElseGet(() -> new Instruction.Raw(printed(cmd), NOT_JSON));
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

    /**
     * One model {@code ARG} per name the instruction declares: rewrite-docker folds {@code ARG A=1 B=2} into one
     * name (upstream issue 12), so the names come from facts' reader and the values, in the model's form, from
     * {@link Pairs}.
     */
    private static List<Instruction.Arg> args(Docker.Arg arg) {
        Map<String, String> declared = FactExtractor.argPairs(arg);
        Docker.Argument value = arg.getValue();
        Map<String, String> values = Pairs.parse(Continuations.removed(
                arg.getName().getText() + (value == null ? "" : "=" + text(value))));
        return declared.entrySet().stream()
                .map(e -> new Instruction.Arg(e.getKey(), e.getValue() == null ? null : values.get(e.getKey())))
                .toList();
    }

    private static Instruction.User user(Docker.User user) {
        Docker.Argument group = user.getGroup();
        return new Instruction.User(braced(unquote(text(user.getUser())))
                + (group == null ? "" : ":" + braced(unquote(text(group)))));
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
        String command = Continuations.oneLine(shell.getArgument().getText());
        return Installs.of(command)
                .<Instruction>map(install -> new Instruction.Run(new RunBody.Install(install)))
                .orElseGet(() -> new Instruction.Run(new RunBody.Shell(List.of(command))));
    }

    /** {@code COPY [--from=x] [--chown=y] src... dest}; other flags and the JSON form stay raw (D2). */
    private static Instruction copy(String text) {
        if (HEREDOC.matcher(text.lines().findFirst().orElse("")).find()) {
            return new Instruction.Raw(text, "COPY with a heredoc");
        }
        List<String> words = new ArrayList<>(List.of(Continuations.oneLine(text).split(" ")));
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

    /** One port written as plain digits: no protocol, range or variable; anything else stays {@code Raw} (D2). */
    private static boolean single(Docker.Expose expose) {
        List<Docker.Port> ports = expose.getPorts();
        return ports.size() == 1 && ports.getFirst().getText().strip().matches("\\d{1,5}");
    }

    /**
     * An exec form's words, decoded as the JSON strings they are ({@code \\"} is a quote); empty when a word is not
     * valid JSON ({@code "\\d+"}), which Docker reads as shell form instead.
     */
    private static Optional<List<String>> arguments(Docker.ExecForm exec) {
        try {
            return Optional.of(exec.getArguments().stream().map(l -> {
                String text = l.getText();
                boolean quoted = text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"");
                return JSON.readValue(quoted ? text : "\"" + text + "\"", String.class);
            }).toList());
        } catch (tools.jackson.core.JacksonException e) {
            return Optional.empty();
        }
    }

    /** The instruction's arguments: comment lines inside it dropped, continuations removed, keyword split off. */
    private static String afterKeyword(String instruction) {
        String text = instruction.lines()
                .filter(line -> !line.strip().startsWith("#"))
                .collect(Collectors.joining("\n"));
        String[] parts = Continuations.removed(text).strip().split("\\s+", 2);
        return parts.length < 2 ? "" : parts[1];
    }

    /**
     * {@code ENV} or {@code LABEL} {@code k=v ...}, or the legacy {@code key value with spaces}, which sets one. Read
     * from the instruction's text, as rewrite-docker 8.90.4 mis-reads pairs in several layouts (upstream issues 4-6).
     */
    private static Map<String, String> pairs(String arguments) {
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

    /**
     * Variables written {@code ${NAME}}, as {@link #text} writes the ones the parser finds, so a quoted
     * {@code "$UID"} and a bare {@code $UID} model alike.
     */
    static String braced(String value) {
        return BARE_VARIABLE.matcher(value).replaceAll("\\${$1}");
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
