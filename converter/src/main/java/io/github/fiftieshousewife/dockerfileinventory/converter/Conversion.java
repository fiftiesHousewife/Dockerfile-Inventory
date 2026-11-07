package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import java.util.List;

record Conversion(Dockerfile model, List<List<List<String>>> comments) {

    Conversion {
        comments = List.copyOf(comments.stream().map(stage -> List.copyOf(stage.stream().map(List::copyOf).toList()))
                .toList());
    }

    List<Instruction.Raw> raw() {
        return model.stages().stream()
                .flatMap(stage -> stage.instructions().stream())
                .<Instruction.Raw>mapMulti((instruction, raw) -> {
                    if (instruction instanceof Instruction.Raw r) {
                        raw.accept(r);
                    }
                })
                .toList();
    }
}
