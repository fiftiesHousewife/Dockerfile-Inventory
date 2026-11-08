package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;

/** One stage of a Dockerfile: its name (or null), its base or the earlier stage it builds on, what it copies from. */
public record ImageStage(String name, BaseImage base, String fromStage, List<String> copiesFrom) {

    public ImageStage {
        copiesFrom = List.copyOf(copiesFrom);
    }
}
