package io.github.fiftieshousewife.dockerfile.model;

public record Workdir(String path) implements Instruction {

    public Workdir {
        Checks.notBlank(path, "a working directory");
    }
}
