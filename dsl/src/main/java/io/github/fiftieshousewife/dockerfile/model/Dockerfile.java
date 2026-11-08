package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** A whole Dockerfile: global build arguments, then stages; the last stage is the image that ships. */
public record Dockerfile(List<Arg> globalArgs, List<Stage> stages) {

    public Dockerfile {
        globalArgs = List.copyOf(globalArgs);
        stages = List.copyOf(stages);
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("a Dockerfile needs at least one stage");
        }
    }
}
