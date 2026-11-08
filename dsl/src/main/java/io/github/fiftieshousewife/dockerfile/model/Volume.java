package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** {@code jsonForm}: written {@code VOLUME ["/data"]} rather than {@code VOLUME /data}, as the original was. */
public record Volume(List<String> paths, boolean jsonForm) implements Instruction {

    public Volume {
        paths = List.copyOf(Checks.notEmpty(paths, "a VOLUME path"));
    }
}
