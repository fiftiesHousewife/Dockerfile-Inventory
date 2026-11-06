package io.github.fiftieshousewife.dockerfileinventory.dsl;

final class Checks {

    private Checks() {
    }

    static String notBlank(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value;
    }
}
