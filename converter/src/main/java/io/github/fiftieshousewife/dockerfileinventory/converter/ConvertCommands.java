package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import java.util.List;
import java.util.Optional;
import org.openrewrite.docker.tree.Docker;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class ConvertCommands {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ConvertCommands() {
    }

    static Instruction run(Docker.Run run, String printed) {
        List<Docker.Flag> flags = run.getFlags();
        if (flags != null && !flags.isEmpty()) {
            return new Instruction.Raw(printed, "RUN with flags");
        }
        if (!(run.getCommand() instanceof Docker.ShellForm shell)) {
            return new Instruction.Raw(printed, "RUN in exec or heredoc form");
        }
        String command = Continuations.oneLine(shell.getArgument().getText());
        return Installs.of(command)
                .<Instruction>map(install -> new Instruction.Run(new RunBody.Install(install)))
                .orElseGet(() -> new Instruction.Run(new RunBody.Shell(List.of(command))));
    }

    // Empty when a word is not valid JSON ("\d+"): Docker then reads the whole instruction as shell form.
    static Optional<List<String>> arguments(Docker.ExecForm exec) {
        try {
            return Optional.of(exec.getArguments().stream().map(word -> decoded(word.getText())).toList());
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    static String decoded(String word) {
        boolean quoted = word.length() >= 2 && word.startsWith("\"") && word.endsWith("\"");
        return JSON.readValue(quoted ? word : "\"" + word + "\"", String.class);
    }
}
