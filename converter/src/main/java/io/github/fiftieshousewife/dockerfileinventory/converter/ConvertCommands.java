package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunFlag;
import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class ConvertCommands {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PLAIN_MARKER = Pattern.compile("(?s)(.*?)\\s*<<(['\"]?)(\\w+)\\2");

    private ConvertCommands() {
    }

    static Instruction run(Docker.Run run, String printed) {
        Optional<List<RunFlag>> flags = flags(run);
        if (flags.isEmpty()) {
            return new Instruction.Raw(printed, "RUN with flag --" + name(written(run).stream()
                    .filter(flag -> flag(flag).isEmpty()).findFirst().orElseThrow()));
        }
        return body(run.getCommand())
                .<Instruction>map(body -> new Instruction.Run(body, flags.get()))
                .orElseGet(() -> new Instruction.Raw(printed, run.getCommand() instanceof Docker.ExecForm
                        ? "RUN in exec form that is not JSON" : "RUN with a heredoc that is not one plain script"));
    }

    static Optional<RunBody> body(Docker.CommandForm command) {
        return switch (command) {
            case Docker.ShellForm shell -> Optional.of(shell(Continuations.oneLine(shell.getArgument().getText())));
            case Docker.ExecForm exec -> arguments(exec).map(RunBody.Exec::new);
            case Docker.HeredocForm heredoc -> heredoc(heredoc);
            default -> Optional.empty();
        };
    }

    static RunBody shell(String command) {
        return Installs.of(command).<RunBody>map(RunBody.Install::new)
                .orElseGet(() -> new RunBody.Shell(List.of(command)));
    }

    static Optional<RunBody> heredoc(Docker.HeredocForm form) {
        Matcher marker = PLAIN_MARKER.matcher(form.getPreamble().strip());
        if (form.getBodies().size() != 1 || !marker.matches()) {
            return Optional.empty();
        }
        Docker.HeredocBody body = form.getBodies().getFirst();
        List<String> lines = String.join("", body.getContentLines()).lines().toList();
        return body.getClosing().strip().equals(marker.group(3))
                ? Optional.of(new RunBody.Heredoc(Continuations.oneLine(marker.group(1)), marker.group(3),
                        !marker.group(2).isEmpty(), lines))
                : Optional.empty();
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
