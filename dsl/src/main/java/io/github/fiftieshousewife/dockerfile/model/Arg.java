package io.github.fiftieshousewife.dockerfile.model;

/** A build argument declared in a stage; its default may be null. */
public record Arg(String name, String defaultValue) implements Instruction {

    public Arg {
        Checks.notBlank(name, "an ARG name");
    }
}
