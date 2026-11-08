package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import java.util.List;
import java.util.stream.Stream;

final class LocalSources {

    private LocalSources() {
    }

    static List<String> of(Dockerfile model) {
        return model.stages().stream()
                .flatMap(stage -> stage.instructions().stream())
                .flatMap(instruction -> switch (instruction) {
                    case Instruction.Copy copy when copy.fromStage() == null -> copy.sources().stream();
                    case Instruction.Add add -> add.sources().stream().filter(source -> !remote(source));
                    default -> Stream.<String>empty();
                })
                .toList();
    }

    static boolean remote(String source) {
        return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("git@");
    }
}
