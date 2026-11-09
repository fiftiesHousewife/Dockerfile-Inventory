package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

final class Unconfined implements PodCheck {

    @Override
    public Check id() {
        return Check.D6;
    }

    @Override
    public List<Problem> problems(Workload workload, Profile profile) {
        return workload.pod().containers().stream()
                .filter(container -> "Unconfined".equals(ContainerSettings.seccomp(container, workload.pod())))
                .map(container -> new Problem("container " + container.name()
                        + " runs without a seccomp profile (Unconfined)", Certainty.READ))
                .toList();
    }
}
