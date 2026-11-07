package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

final class Checks {

    private Checks() {
    }

    static String notBlank(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value;
    }

    static <T> List<T> notEmpty(List<T> values, String what) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException(what + " is needed");
        }
        return List.copyOf(values);
    }
}
