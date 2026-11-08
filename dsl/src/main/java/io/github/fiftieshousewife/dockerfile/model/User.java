package io.github.fiftieshousewife.dockerfile.model;

/** A {@code USER}: a numeric UID, or a name, which the policy refuses for the final stage (S4). */
public record User(String user) implements Instruction {

    public User {
        Checks.notBlank(user, "a user");
    }
}
