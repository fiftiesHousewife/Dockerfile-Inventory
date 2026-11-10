package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.util.List;
import java.util.Map;

record Setting(String name, boolean required, String fallback, String requiredWith, List<String> requiredUnless,
               String description) {

    static Setting required(String name, String description) {
        return new Setting(name, true, null, null, null, description);
    }

    static Setting requiredWith(String name, String other, String description) {
        return new Setting(name, false, null, other, null, description);
    }

    static Setting requiredUnless(String name, List<String> others, String description) {
        return new Setting(name, false, null, null, List.copyOf(others), description);
    }

    static Setting optional(String name, String description) {
        return new Setting(name, false, null, null, null, description);
    }

    static Setting optional(String name, String fallback, String description) {
        return new Setting(name, false, fallback, null, null, description);
    }

    boolean requiredIn(Map<String, String> env) {
        return required || requiredWith != null && !unset(env.get(requiredWith))
                || requiredUnless != null && requiredUnless.stream().allMatch(other -> unset(env.get(other)));
    }

    boolean missingFrom(Map<String, String> env) {
        return requiredIn(env) && unset(env.get(name));
    }

    static boolean unset(String value) {
        return value == null || value.isBlank();
    }
}
