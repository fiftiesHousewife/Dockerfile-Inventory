package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.ArrayList;
import java.util.List;

// P9: what the image declares that OpenShift leaves to the deployment.
final class DeploymentHints implements PlatformCheck {

    @Override
    public Check id() {
        return Check.P9;
    }

    @Override
    public Certainty certainty() {
        return Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        List<String> problems = new ArrayList<>();
        String healthcheck = image.config().healthcheck();
        if (healthcheck != null && !healthcheck.equals("NONE")) {
            problems.add("OpenShift ignores HEALTHCHECK: declare a liveness or readiness probe in the deployment");
        }
        image.config().volumes().forEach(volume -> problems.add("VOLUME " + volume + " is a plain directory in the "
                + "container on OpenShift: declare a volume in the deployment for data that must outlive it"));
        return problems;
    }
}
