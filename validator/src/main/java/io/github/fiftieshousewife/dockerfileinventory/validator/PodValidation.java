package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

final class PodValidation {

    static final List<PodCheck> CHECKS = List.of(new Privileged(), new FixedUser(), new AddedCapability(),
            new PrivilegeEscalation(), new HostAccess(), new Unconfined());

    private PodValidation() {
    }

    static List<Finding> findings(Workload workload, Profile profile) {
        return CHECKS.stream().filter(check -> profile.severity().get(check.id()) != Severity.OFF)
                .flatMap(check -> check.problems(workload, profile).stream().map(problem -> new Finding(check.id(),
                        profile.severity().get(check.id()), problem.certainty(), workload.blobSha(),
                        problem.message())))
                .toList();
    }
}
