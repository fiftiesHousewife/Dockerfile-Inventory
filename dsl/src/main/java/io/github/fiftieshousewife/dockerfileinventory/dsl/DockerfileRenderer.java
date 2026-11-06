package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The one renderer: builders and Phase 3's fixes both produce Dockerfile text through it, so new and migrated
 * Dockerfiles converge on the same lines. Output is stable: one model always renders the same text. A {@code RUN}
 * with several commands puts each after {@code &&} on its own line, and {@code ENV} and {@code LABEL} put each pair
 * on its own line, so diffs stay readable.
 */
public final class DockerfileRenderer {



    private static final String CONTINUE = " \\\n";
    private static final Pattern RUN_MOUNT = Pattern.compile("^RUN\\s+(--\\S+\\s+)*--mount=");
    private static final Pattern HEREDOC = Pattern.compile("^(RUN|COPY|ADD)\\s.*<<-?['\"]?\\w+");

    private DockerfileRenderer() {
    }

    public static String render(Dockerfile dockerfile, Target target) {
        List<String> blocks = new ArrayList<>();
        if (!dockerfile.globalArgs().isEmpty()) {
            blocks.add(dockerfile.globalArgs().stream().map(DockerfileRenderer::instruction)
                    .collect(Collectors.joining("\n")));
        }
        dockerfile.stages().forEach(stage -> blocks.add(stage(stage, target)));
        return String.join("\n\n", blocks) + "\n";
    }

    private static String stage(Stage stage, Target target) {
        List<String> lines = new ArrayList<>();
        lines.add("FROM " + (stage.base() != null ? reference(stage.base()) : stage.fromStage())
                + (stage.name() == null ? "" : " AS " + stage.name()));
        stage.instructions().forEach(i -> lines.add(supported(instruction(i), target)));
        return String.join("\n", lines);
    }

    /** Only {@link Instruction.Raw} can hold a heredoc or a mount; the typed model has neither. */
    private static String supported(String line, Target target) {
        if (!target.runMounts() && RUN_MOUNT.matcher(line).find()) {
            throw new IllegalArgumentException("the target does not support RUN --mount: " + line);
        }
        if (!target.heredocs() && HEREDOC.matcher(line).find()) {
            throw new IllegalArgumentException("the target does not support a heredoc: " + line);
        }
        return line;
    }

    private static String reference(BaseImage base) {
        return base.name() + (base.tag() == null ? "" : ":" + base.tag())
                + (base.digest() == null ? "" : "@" + base.digest());
    }

    static String instruction(Instruction instruction) {
        return switch (instruction) {
            case Instruction.Arg arg -> "ARG " + arg.name()
                    + (arg.defaultValue() == null ? "" : "=" + quote(arg.defaultValue()));
            case Instruction.Env env -> pairs("ENV", env.values());
            case Instruction.Label label -> pairs("LABEL", label.values());
            case Instruction.Run run -> "RUN " + commands(run.body());
            case Instruction.Copy copy -> "COPY" + (copy.fromStage() == null ? "" : " --from=" + copy.fromStage())
                    + (copy.chown() == null ? "" : " --chown=" + copy.chown())
                    + " " + String.join(" ", copy.sources()) + " " + copy.destination();
            case Instruction.User user -> "USER " + user.user();
            case Instruction.Workdir workdir -> "WORKDIR " + workdir.path();
            case Instruction.Expose expose -> "EXPOSE " + expose.port();
            case Instruction.Entrypoint entrypoint -> "ENTRYPOINT " + json(entrypoint.command());
            case Instruction.Cmd cmd -> "CMD " + json(cmd.command());
            case Instruction.Raw raw -> raw.text();
        };
    }

    private static String commands(RunBody body) {
        List<String> commands = switch (body) {
            case RunBody.Install install -> PackageInstallRenderer.commands(install.install());
            case RunBody.Shell shell -> shell.commands();
        };
        return String.join(CONTINUE + "    && ", commands);
    }

    private static String pairs(String keyword, Map<String, String> values) {
        String indent = " ".repeat(keyword.length() + 1);
        return keyword + " " + values.entrySet().stream()
                .map(e -> e.getKey() + "=" + quote(e.getValue()))
                .collect(Collectors.joining(CONTINUE + indent));
    }

    /**
     * Double-quoted, so spaces survive and {@code $VARIABLES} still expand as Docker expands them. A model value is
     * written as it would be inside double quotes: its escaped pairs ({@code \\$}, a literal dollar, and
     * {@code \\\\}, a literal backslash) are kept; any other backslash is doubled and a quote escaped.
     */
    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            char next = i + 1 < value.length() ? value.charAt(i + 1) : 0;
            if (c == '\\' && (next == '\\' || next == '$')) {
                quoted.append(c).append(next);
                i++;
            } else if (c == '\\' || c == '"') {
                quoted.append('\\').append(c);
            } else {
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }

    private static String json(List<String> command) {
        return command.stream().map(DockerfileRenderer::jsonString).collect(Collectors.joining(", ", "[", "]"));
    }

    /** A JSON string: every backslash escaped, as JSON has no {@code \$}, and control characters too. */
    private static String jsonString(String value) {
        StringBuilder json = new StringBuilder("\"");
        value.chars().forEach(c -> json.append(switch (c) {
            case '\\' -> "\\\\";
            case '"' -> "\\\"";
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            case '\r' -> "\\r";
            default -> c < 0x20 ? String.format(java.util.Locale.ROOT, "\\u%04x", c) : String.valueOf((char) c);
        }));
        return json.append('"').toString();
    }
}
