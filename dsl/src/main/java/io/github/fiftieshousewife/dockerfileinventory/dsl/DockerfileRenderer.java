package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The one renderer: builders and fixes both produce Dockerfile text through it, so new and migrated Dockerfiles
 * converge on the same lines. One command or pair per line keeps diffs readable.
 */
public final class DockerfileRenderer {

    private static final String CONTINUE = " \\\n";
    private static final Pattern RUN_MOUNT = Pattern.compile("^RUN\\s+(--\\S+\\s+)*--mount=");
    private static final Pattern HEREDOC = Pattern.compile("^(RUN|COPY|ADD)\\s.*<<-?['\"]?\\w+");

    private DockerfileRenderer() {
    }

    /** Fails when the model needs a feature the target's builder lacks, rather than write text it cannot read. */
    public static String render(Dockerfile dockerfile, Target target) {
        List<String> blocks = new ArrayList<>();
        if (!dockerfile.globalArgs().isEmpty()) {
            blocks.add(dockerfile.globalArgs().stream().map(DockerfileRenderer::instruction)
                    .map(DockerfileRenderer::ended).collect(Collectors.joining("\n")));
        }
        dockerfile.stages().forEach(stage -> blocks.add(stage(stage, target)));
        return String.join("\n\n", blocks) + "\n";
    }

    static String stage(Stage stage, Target target) {
        List<String> lines = new ArrayList<>();
        lines.add(from(stage));
        stage.instructions().forEach(i -> lines.add(ended(supported(instruction(i), target))));
        return String.join("\n", lines);
    }

    static String from(Stage stage) {
        return "FROM " + (stage.base() != null ? reference(stage.base()) : stage.fromStage())
                + (stage.name() == null ? "" : " AS " + stage.name());
    }

    // A last word ending in a backslash (C:\Windows\) would join the next line to it; a blank line stops that.
    static String ended(String instruction) {
        return instruction.endsWith("\\") ? instruction + "\n" : instruction;
    }

    static String supported(String line, Target target) {
        if (!target.runMounts() && RUN_MOUNT.matcher(line).find()) {
            throw new IllegalArgumentException("the target does not support RUN --mount: " + line);
        }
        if (!target.heredocs() && HEREDOC.matcher(line).find()) {
            throw new IllegalArgumentException("the target does not support a heredoc: " + line);
        }
        return line;
    }

    static String reference(BaseImage base) {
        return base.name() + (base.tag() == null ? "" : ":" + base.tag())
                + (base.digest() == null ? "" : "@" + base.digest());
    }

    static String instruction(Instruction instruction) {
        return switch (instruction) {
            case Instruction.Arg arg -> "ARG " + arg.name()
                    + (arg.defaultValue() == null ? "" : "=" + DockerQuotes.doubleQuoted(arg.defaultValue()));
            case Instruction.Env env -> pairs("ENV", env.values());
            case Instruction.Label label -> pairs("LABEL", label.values());
            case Instruction.Run run -> "RUN " + run.flags().stream().map(flag -> flag.written() + " ")
                    .collect(Collectors.joining()) + body(run.body());
            case Instruction.Copy copy -> copy(copy);
            case Instruction.CopyHeredoc heredoc -> heredoc(heredoc);
            case Instruction.Add add -> add(add);
            case Instruction.User user -> "USER " + user.user();
            case Instruction.Workdir workdir -> "WORKDIR " + workdir.path();
            case Instruction.Expose expose -> "EXPOSE "
                    + expose.ports().stream().map(Port::spec).collect(Collectors.joining(" "));
            case Instruction.Entrypoint entrypoint -> "ENTRYPOINT " + DockerQuotes.jsonArray(entrypoint.command());
            case Instruction.Cmd cmd -> "CMD " + DockerQuotes.jsonArray(cmd.command());
            case Instruction.Shell shell -> "SHELL " + DockerQuotes.jsonArray(shell.command());
            case Instruction.Volume volume -> "VOLUME " + (volume.jsonForm() ? DockerQuotes.jsonArray(volume.paths())
                    : String.join(" ", volume.paths()));
            case Instruction.StopSignal stopSignal -> "STOPSIGNAL " + stopSignal.signal();
            case Instruction.Maintainer maintainer -> "MAINTAINER " + maintainer.name();
            case Instruction.Raw raw -> raw.text();
        };
    }

    static String copy(Instruction.Copy copy) {
        return "COPY" + (copy.fromStage() == null ? "" : " --from=" + copy.fromStage())
                + (copy.chown() == null ? "" : " --chown=" + copy.chown())
                + (copy.chmod() == null ? "" : " --chmod=" + copy.chmod())
                + (copy.link() ? " --link" : "")
                + " " + paths(copy.sources(), copy.destination(), copy.jsonForm());
    }

    static String add(Instruction.Add add) {
        return "ADD" + (add.chown() == null ? "" : " --chown=" + add.chown())
                + (add.chmod() == null ? "" : " --chmod=" + add.chmod())
                + (add.checksum() == null ? "" : " --checksum=" + add.checksum())
                + (add.keepGitDir() ? " --keep-git-dir" : "")
                + (add.link() ? " --link" : "")
                + " " + paths(add.sources(), add.destination(), add.jsonForm());
    }

    static String body(RunBody body) {
        return switch (body) {
            case RunBody.Exec exec -> DockerQuotes.jsonArray(exec.arguments());
            case RunBody.Heredoc heredoc -> (heredoc.command().isEmpty() ? "" : heredoc.command() + " ")
                    + heredoc(heredoc.delimiter(), heredoc.quoted(), "", heredoc.lines());
            case RunBody.Install install -> String.join(CONTINUE + "    && ", RunCommands.of(install));
            case RunBody.Shell shell -> String.join(CONTINUE + "    && ", RunCommands.of(shell));
        };
    }

    static String heredoc(Instruction.CopyHeredoc heredoc) {
        return "COPY " + heredoc(heredoc.delimiter(), heredoc.quoted(), heredoc.destination(), heredoc.lines());
    }

    static String heredoc(String delimiter, boolean quoted, String after, List<String> lines) {
        return "<<" + (quoted ? "'" + delimiter + "'" : delimiter) + (after.isEmpty() ? "" : " " + after) + "\n"
                + lines.stream().map(line -> line + "\n").collect(Collectors.joining()) + delimiter;
    }

    static String paths(List<String> sources, String destination, boolean jsonForm) {
        List<String> paths = new ArrayList<>(sources);
        paths.add(destination);
        return jsonForm ? DockerQuotes.jsonArray(paths) : String.join(" ", paths);
    }

    static String pairs(String keyword, Map<String, String> values) {
        String indent = " ".repeat(keyword.length() + 1);
        return keyword + " " + values.entrySet().stream()
                .map(e -> e.getKey() + "=" + DockerQuotes.doubleQuoted(e.getValue()))
                .collect(Collectors.joining(CONTINUE + indent));
    }
}
