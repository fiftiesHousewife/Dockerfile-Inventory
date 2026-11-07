package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class Checks {

    private static final Set<String> HEALTHCHECK_OPTIONS =
            Set.of("interval", "timeout", "start-period", "start-interval", "retries");

    private Checks() {
    }

    static String notBlank(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value;
    }

    static void healthcheck(Map<String, String> options, List<String> command, CommandForm form) {
        options.keySet().stream().filter(option -> !HEALTHCHECK_OPTIONS.contains(option)).findFirst()
                .ifPresent(option -> {
                    throw new IllegalArgumentException("not a HEALTHCHECK option: " + option);
                });
        if (command.isEmpty() && !options.isEmpty()) {
            throw new IllegalArgumentException("HEALTHCHECK NONE takes no options");
        }
        if (form == CommandForm.SHELL && command.size() != 1) {
            throw new IllegalArgumentException("a shell-form command is one string, not " + command);
        }
    }

    static <T> List<T> notEmpty(List<T> values, String what) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException(what + " is needed");
        }
        return List.copyOf(values);
    }
}
