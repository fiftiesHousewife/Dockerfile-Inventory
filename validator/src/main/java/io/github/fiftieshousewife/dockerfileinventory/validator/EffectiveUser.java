package io.github.fiftieshousewife.dockerfileinventory.validator;

record EffectiveUser(String user, String setBy) {

    // Worst first.
    enum Kind {
        ROOT,
        NAMED,
        NUMERIC
    }

    Kind kind() {
        String name = user == null ? "" : user.split(":", -1)[0];
        if (name.isEmpty() || name.equals("root") || name.equals("0")) {
            return Kind.ROOT;
        }
        return name.chars().allMatch(Character::isDigit) ? Kind.NUMERIC : Kind.NAMED;
    }

    static EffectiveUser worse(EffectiveUser one, EffectiveUser other) {
        return other.kind().compareTo(one.kind()) < 0 ? other : one;
    }
}
