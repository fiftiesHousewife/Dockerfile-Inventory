package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;
import java.util.stream.Stream;

final class ContainerImage {

    private ContainerImage() {
    }

    static List<Finding> findings(Subject image, Workload.Container container, Profile profile) {
        Severity p9 = profile.severity().get(Check.P9);
        Stream<Finding> kept = Validation.findings(image, profile).stream().filter(f -> f.check() != Check.P9);
        Stream<Finding> unanswered = p9 == Severity.OFF ? Stream.of() : DeploymentHints.unanswered(image, container)
                .stream().map(problem -> new Finding(Check.P9, p9, Certainty.READ, image.blobSha(), problem));
        return Stream.concat(kept, unanswered).toList();
    }
}
