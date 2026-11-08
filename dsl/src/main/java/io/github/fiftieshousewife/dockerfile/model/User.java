package io.github.fiftieshousewife.dockerfile.model;

public record User(String user) implements Instruction {

    public User {
        Checks.notBlank(user, "a user");
    }
}
