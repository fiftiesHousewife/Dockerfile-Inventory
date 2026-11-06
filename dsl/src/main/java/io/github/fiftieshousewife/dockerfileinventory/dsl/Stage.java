package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

/** {@code FROM} a base image, or from an earlier stage by name; {@code name} is optional for the final stage. */
public record Stage(String name, BaseImage base, String fromStage, List<Instruction> instructions) {

    public Stage {
        instructions = List.copyOf(instructions);
        if ((base == null) == (fromStage == null)) {
            throw new IllegalArgumentException("a stage builds on a base image or an earlier stage, exactly one");
        }
    }
}
