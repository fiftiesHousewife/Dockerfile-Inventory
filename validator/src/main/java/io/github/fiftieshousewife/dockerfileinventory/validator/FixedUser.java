package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;
import java.util.Optional;

final class FixedUser implements PodCheck {

    @Override
    public Check id() {
        return Check.D2;
    }

    @Override
    public List<Problem> problems(Workload workload, Profile profile) {
        return workload.pod().containers().stream()
                .flatMap(container -> Optional.ofNullable(ContainerSettings.runAsUser(container, workload.pod()))
                        .map(uid -> problem(container.name(), uid)).stream())
                .toList();
    }

    static Problem problem(String container, long uid) {
        return uid == 0 ? new Problem("container " + container + " runs as UID 0", Certainty.READ)
                : new Problem("container " + container + " runs as UID " + uid
                        + ", admitted only if the namespace's range holds it", Certainty.INFERRED);
    }
}
