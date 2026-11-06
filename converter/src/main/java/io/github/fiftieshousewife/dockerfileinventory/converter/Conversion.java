package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import java.util.List;

/**
 * A Dockerfile converted to the model, with the comment lines before each instruction (per stage, per
 * instruction), so the generated class can carry them.
 */
public record Conversion(Dockerfile model, List<List<List<String>>> comments) {

    public Conversion {
        comments = List.copyOf(comments.stream().map(stage -> List.copyOf(stage.stream().map(List::copyOf).toList()))
                .toList());
    }

    /** Every untyped instruction, with its reason: Phase 5's builder backlog. */
    public List<Instruction.Raw> raw() {
        return model.stages().stream()
                .flatMap(stage -> stage.instructions().stream())
                .filter(i -> i instanceof Instruction.Raw)
                .map(i -> (Instruction.Raw) i)
                .toList();
    }
}
