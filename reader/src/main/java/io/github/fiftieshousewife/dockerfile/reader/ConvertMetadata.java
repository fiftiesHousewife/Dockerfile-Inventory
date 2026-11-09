package io.github.fiftieshousewife.dockerfile.reader;

import io.github.fiftieshousewife.dockerfile.reader.internal.Values;
import io.github.fiftieshousewife.dockerfile.model.Healthcheck;
import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.Shell;
import io.github.fiftieshousewife.dockerfile.model.Volume;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.model.CommandForm;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;

final class ConvertMetadata {

    private ConvertMetadata() {
    }

    static Instruction shell(Docker.Shell shell, String printed) {
        try {
            return new Shell(shell.getArguments().stream()
                    .map(word -> ConvertCommands.decoded(Values.text(word))).toList());
        } catch (JacksonException e) {
            return ConvertInstruction.notModelled(printed);
        }
    }

    static Instruction volume(Docker.Volume volume, String printed) {
        try {
            List<String> paths = volume.getValues().stream()
                    .map(path -> volume.isJsonForm() ? ConvertCommands.decoded(Values.text(path))
                            : Values.written(path))
                    .toList();
            return new Volume(paths, volume.isJsonForm());
        } catch (JacksonException e) {
            return ConvertInstruction.notModelled(printed);
        }
    }

    static Instruction healthcheck(Docker.Healthcheck healthcheck, String printed) {
        if (healthcheck.isNone()) {
            return Healthcheck.NONE;
        }
        Map<String, String> options = new LinkedHashMap<>();
        Values.flags(healthcheck.getFlags())
                .forEach(flag -> options.put(Values.flagName(flag), Values.flagValue(flag)));
        Docker.CommandForm command = Objects.requireNonNull(healthcheck.getCmd()).getCommand();
        try {
            return switch (command) {
                case Docker.ExecForm exec -> ConvertCommands.arguments(exec)
                        .<Instruction>map(words -> new Healthcheck(options, words, CommandForm.EXEC))
                        .orElseGet(() -> new Raw(printed, "HEALTHCHECK in exec form that is not JSON"));
                case Docker.ShellForm shell -> new Healthcheck(options,
                        List.of(ConvertCommands.shellText(shell)), CommandForm.SHELL);
                default -> ConvertInstruction.notModelled(printed);
            };
        } catch (IllegalArgumentException notDockers) {
            return new Raw(printed, "HEALTHCHECK with an option Docker lacks");
        }
    }
}
