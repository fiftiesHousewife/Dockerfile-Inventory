package io.github.fiftieshousewife.dockerfile.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Values as written inside double quotes: {@code $NAME} expands, {@code \$} is a dollar, {@code \\} a
 * backslash.
 */
public record Label(Map<String, String> values) implements Instruction {

    public Label {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
