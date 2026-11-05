package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;

/** What one Dockerfile builds, reduced to the facts clustering and rules need. Describes the final stage. */
public record ImageFacts(int stageCount, BaseImage base, List<StagedCall> packageCalls, int runs, int opaqueRuns,
                         String finalUser, UserKind userKind, List<String> exposedPorts,
                         EntrypointForm entrypointForm, List<String> buildKitFeatures) {

    public ImageFacts {
        packageCalls = List.copyOf(packageCalls);
        exposedPorts = List.copyOf(exposedPorts);
        buildKitFeatures = List.copyOf(buildKitFeatures);
    }
}
