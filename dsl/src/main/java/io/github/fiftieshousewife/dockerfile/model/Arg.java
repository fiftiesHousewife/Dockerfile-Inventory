package io.github.fiftieshousewife.dockerfile.model;

public record Arg(String name, String defaultValue) implements Instruction {

    public Arg {
        Checks.notBlank(name, "an ARG name");
    }
}
