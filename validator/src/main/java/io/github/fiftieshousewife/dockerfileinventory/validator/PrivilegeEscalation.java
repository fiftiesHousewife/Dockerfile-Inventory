package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

final class PrivilegeEscalation implements PodCheck {

    @Override
    public Check id() {
        return Check.D4;
    }

    @Override
    public List<Problem> problems(Workload workload, Profile profile) {
        return workload.pod().containers().stream()
                .filter(container -> Boolean.TRUE.equals(container.security().allowPrivilegeEscalation()))
                .map(container -> new Problem("container " + container.name() + " allows privilege escalation",
                        Certainty.READ))
                .toList();
    }
}
