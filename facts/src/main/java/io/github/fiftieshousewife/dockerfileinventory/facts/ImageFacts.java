package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;

/** What one Dockerfile builds, reduced to the facts clustering and rules need. Describes the final stage. */
public record ImageFacts(int stageCount, BaseImage base, List<StagedCall> packageCalls, int runs, int opaqueRuns) {

    public ImageFacts {
        packageCalls = List.copyOf(packageCalls);
    }
}
