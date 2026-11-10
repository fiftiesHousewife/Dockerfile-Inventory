package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

final class Validation {

    static final List<PlatformCheck> CHECKS = List.of(new FinalUser(), new WritablePaths(), new LowPorts(),
            new UserSwitch(), new SetuidBits(), new ShellStart(), new BaseRegistry(), new UnpinnedBase(),
            new DeploymentHints(), new UserIdentity(), new FloatingBase());

    private Validation() {
    }

    static List<Finding> findings(Subject image, Profile profile) {
        return CHECKS.stream().filter(check -> profile.severity().get(check.id()) != Severity.OFF)
                .flatMap(check -> check.problems(image, profile).stream().map(problem -> new Finding(check.id(),
                        profile.severity().get(check.id()), check.certainty(image), image.blobSha(), problem)))
                .toList();
    }
}
