package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

/** A built image: its model, and every {@link Instruction.Raw} it uses, for review (D3). */
public record Built(Dockerfile dockerfile, List<Instruction.Raw> raw) {

    public Built {
        raw = List.copyOf(raw);
    }
}
