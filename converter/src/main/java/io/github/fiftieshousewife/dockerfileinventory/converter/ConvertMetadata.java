package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import java.util.List;
import tools.jackson.core.JacksonException;

final class ConvertMetadata {

    private ConvertMetadata() {
    }

    static Instruction shell(Docker.Shell shell, String printed) {
        try {
            return new Instruction.Shell(shell.getArguments().stream()
                    .map(word -> ConvertCommands.decoded(Values.text(word))).toList());
        } catch (JacksonException e) {
            return Convert.notModelled(printed);
        }
    }

    static Instruction volume(Docker.Volume volume, String printed) {
        try {
            List<String> paths = volume.getValues().stream()
                    .map(path -> volume.isJsonForm() ? ConvertCommands.decoded(Values.text(path))
                            : Values.written(path))
                    .toList();
            return new Instruction.Volume(paths, volume.isJsonForm());
        } catch (JacksonException e) {
            return Convert.notModelled(printed);
        }
    }
}
