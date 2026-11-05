package io.github.fiftieshousewife.dockerfileinventory.facts;

/** What one Dockerfile builds, reduced to the facts clustering and rules need. Describes the final stage. */
public record ImageFacts(int stageCount, BaseImage base) {
}
