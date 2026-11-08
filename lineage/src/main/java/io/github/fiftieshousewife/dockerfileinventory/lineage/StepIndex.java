package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Candidates for an image found by what its steps hold, not by its base: a release pipeline may record no FROM in
// the image's history, or build it on another base than its Dockerfile names.
final class StepIndex {

    // A step in more Dockerfiles than this (USER 1001, WORKDIR /app) says little about which built an image.
    static final int RARE = 20;

    private final Map<Instruction, List<Dockerfile>> byStep = new HashMap<>();

    StepIndex(List<Dockerfile> dockerfiles) {
        dockerfiles.forEach(dockerfile -> dockerfile.steps().stream().filter(StepIndex::indexed).distinct()
                .forEach(step -> byStep.computeIfAbsent(step, key -> new ArrayList<>()).add(dockerfile)));
    }

    List<Dockerfile> candidates(List<Instruction> own) {
        Set<Dockerfile> candidates = new LinkedHashSet<>();
        own.stream().filter(StepIndex::indexed).distinct().map(step -> byStep.getOrDefault(step, List.of()))
                .filter(holders -> holders.size() <= RARE).forEach(candidates::addAll);
        return List.copyOf(candidates);
    }

    private static boolean indexed(Instruction step) {
        return !step.keyword().equals("LABEL");
    }
}
