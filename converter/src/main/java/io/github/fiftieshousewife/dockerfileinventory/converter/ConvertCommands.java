package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunFlag;
import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import java.util.List;
import java.util.Optional;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class ConvertCommands {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ConvertCommands() {
    }

    static Instruction run(Docker.Run run, String printed) {
        Optional<List<RunFlag>> flags = flags(run);
        if (flags.isEmpty()) {
            return new Instruction.Raw(printed, "RUN with flag --" + name(written(run).stream()
                    .filter(flag -> flag(flag).isEmpty()).findFirst().orElseThrow()));
        }
        if (!(run.getCommand() instanceof Docker.ShellForm shell)) {
            return new Instruction.Raw(printed, "RUN in exec or heredoc form");
        }
        String command = Continuations.oneLine(shell.getArgument().getText());
        return Installs.of(command)
                .<Instruction>map(install -> new Instruction.Run(new RunBody.Install(install), flags.get()))
                .orElseGet(() -> new Instruction.Run(new RunBody.Shell(List.of(command)), flags.get()));
    }

    // Empty when any flag is one the model lacks.
    static Optional<List<RunFlag>> flags(Docker.Run run) {
        List<Optional<RunFlag>> flags = written(run).stream().map(ConvertCommands::flag).toList();
        return flags.stream().allMatch(Optional::isPresent)
                ? Optional.of(flags.stream().map(Optional::orElseThrow).toList()) : Optional.empty();
    }

    static Optional<RunFlag> flag(Docker.Flag flag) {
        Docker.Argument argument = flag.getValue();
        String value = argument == null ? "" : Values.written(argument);
        return switch (name(flag)) {
            case "mount" -> Optional.of(RunFlag.Mount.parse(value));
            case "network" -> Optional.of(new RunFlag.Network(value));
            case "security" -> Optional.of(new RunFlag.Security(value));
            default -> Optional.empty();
        };
    }

    private static String name(Docker.Flag flag) {
        return flag.getName().replaceFirst("^-+", "");
    }

    private static List<Docker.Flag> written(Docker.Run run) {
        return run.getFlags() == null ? List.of() : run.getFlags();
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
