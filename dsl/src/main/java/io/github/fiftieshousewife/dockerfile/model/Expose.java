package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** An {@code EXPOSE} of the ports the image listens on. */
public record Expose(List<Port> ports) implements Instruction {

    public Expose {
        ports = List.copyOf(Checks.notEmpty(ports, "an EXPOSE port"));
    }
}
