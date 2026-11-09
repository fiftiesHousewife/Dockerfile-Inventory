package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

final class Privileged implements PodCheck {

    @Override
    public Check id() {
        return Check.D1;
    }

    @Override
    public List<Problem> problems(Workload workload, Profile profile) {
        return workload.pod().containers().stream()
                .filter(container -> Boolean.TRUE.equals(container.security().privileged()))
                .map(container -> new Problem("container " + container.name() + " is privileged", Certainty.READ))
                .toList();
    }
}
