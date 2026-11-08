package io.github.fiftieshousewife.dockerfile.model;

/** Deprecated by Docker in favour of a label; kept so a converted Dockerfile reads as it did. */
public record Maintainer(String name) implements Instruction {

    public Maintainer {
        Checks.notBlank(name, "a maintainer");
    }
}
