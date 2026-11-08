package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunFlag;
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

    static List<String> secretIds(Dockerfile model) {
        return model.stages().stream()
                .flatMap(stage -> stage.instructions().stream())
                .<RunFlag>mapMulti((instruction, flags) -> {
                    if (instruction instanceof Instruction.Run run) {
                        run.flags().forEach(flags);
                    }
                })
                .<String>mapMulti((flag, ids) -> {
                    if (flag instanceof RunFlag.Mount mount && mount.type().equals("secret")) {
                        mount.options().stream().filter(option -> option.startsWith("id="))
                                .forEach(option -> ids.accept(option.substring(3)));
                    }
                })
                .distinct()
                .toList();
    }

    static boolean remote(String source) {
        return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("git@");
    }
}
