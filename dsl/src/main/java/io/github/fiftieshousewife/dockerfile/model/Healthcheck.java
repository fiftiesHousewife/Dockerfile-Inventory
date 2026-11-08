package io.github.fiftieshousewife.dockerfile.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code HEALTHCHECK}: options ({@code interval}, {@code timeout}, {@code start-period}, {@code start-interval},
 * {@code retries}) in the order written, then the command; {@link #NONE} turns off a base's check.
 */
public record Healthcheck(Map<String, String> options, List<String> command, CommandForm form) implements Instruction {

    public static final Healthcheck NONE = new Healthcheck(Map.of(), List.of(), CommandForm.EXEC);

    public Healthcheck {
        options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
        command = List.copyOf(command);
        Checks.healthcheck(options, command, form);
    }
}
