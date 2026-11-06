package io.github.fiftieshousewife.dockerfileinventory.report;

/** Who an image runs as, the facts stage's classification applied to an effective user. */
public enum UserKind {
    ROOT,
    NUMERIC,
    NAMED,
    UNRESOLVED;

    /** The user part of {@code user:group}; none, empty, {@code root} or {@code 0} is root, as Docker runs it. */
    static UserKind of(String user) {
        String name = user == null ? "" : user.split(":", 2)[0].strip();
        if (name.isEmpty() || name.equals("root") || name.equals("0")) {
            return ROOT;
        }
        if (name.contains("$")) {
            return UNRESOLVED;
        }
        return name.chars().allMatch(Character::isDigit) ? NUMERIC : NAMED;
    }
}
