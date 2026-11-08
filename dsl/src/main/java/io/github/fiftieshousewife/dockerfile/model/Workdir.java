package io.github.fiftieshousewife.dockerfile.model;

/** A {@code WORKDIR}: where later instructions and the running process start. */
public record Workdir(String path) implements Instruction {

    public Workdir {
        Checks.notBlank(path, "a working directory");
    }
}
