package io.github.fiftieshousewife.dockerfile.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class InstructionParts {

    private static final String CONTINUE = " \\\n";

    private InstructionParts() {
    }

    static String copy(Copy copy) {
        return "COPY" + (copy.fromStage() == null ? "" : " --from=" + copy.fromStage())
                + (copy.chown() == null ? "" : " --chown=" + copy.chown())
                + (copy.chmod() == null ? "" : " --chmod=" + copy.chmod())
                + (copy.link() ? " --link" : "")
                + " " + paths(copy.sources(), copy.destination(), copy.jsonForm());
    }

    static String add(Add add) {
        return "ADD" + (add.chown() == null ? "" : " --chown=" + add.chown())
                + (add.chmod() == null ? "" : " --chmod=" + add.chmod())
                + (add.checksum() == null ? "" : " --checksum=" + add.checksum())
                + (add.keepGitDir() ? " --keep-git-dir" : "")
                + (add.link() ? " --link" : "")
                + " " + paths(add.sources(), add.destination(), add.jsonForm());
    }

    static String healthcheck(Healthcheck healthcheck) {
        if (healthcheck.command().isEmpty()) {
            return "HEALTHCHECK NONE";
        }
        return "HEALTHCHECK " + healthcheck.options().entrySet().stream()
                .map(option -> "--" + option.getKey() + "=" + option.getValue() + " ").collect(Collectors.joining())
                + "CMD " + command(healthcheck.command(), healthcheck.form());
    }

    static String command(List<String> command, CommandForm form) {
        return form == CommandForm.EXEC ? DockerQuotes.jsonArray(command) : command.getFirst();
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

    static String heredoc(CopyHeredoc heredoc) {
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
}
