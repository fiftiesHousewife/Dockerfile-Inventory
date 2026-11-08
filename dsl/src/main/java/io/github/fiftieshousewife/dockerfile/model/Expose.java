package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

public record Expose(List<Port> ports) implements Instruction {

    public Expose {
        ports = List.copyOf(Checks.notEmpty(ports, "an EXPOSE port"));
    }
}
