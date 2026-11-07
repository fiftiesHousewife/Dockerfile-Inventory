package io.github.fiftieshousewife.dockerfileinventory.report;

enum UserKind {
    ROOT,
    NUMERIC,
    NAMED,
    UNRESOLVED;

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
