package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// What the image declares that OpenShift leaves to the deployment.
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
        return hints(image, false, Set.of());
    }

    static List<String> unanswered(Subject image, Workload.Container container) {
        return hints(image, container.probes().liveness() || container.probes().readiness(),
                container.mountPaths().stream().map(DeploymentHints::path).collect(Collectors.toSet()));
    }

    static List<String> hints(Subject image, boolean probed, Set<String> mounted) {
        String healthcheck = image.config().healthcheck();
        Stream<String> probe = healthcheck == null || healthcheck.equals("NONE") || probed ? Stream.of()
                : Stream.of("OpenShift ignores HEALTHCHECK: declare a liveness or readiness probe in the deployment");
        Stream<String> volumes = image.config().volumes().stream().filter(volume -> !mounted.contains(path(volume)))
                .map(volume -> "VOLUME " + volume + " is a plain directory in the container on OpenShift: declare a "
                        + "volume in the deployment for data that must outlive it");
        return Stream.concat(probe, volumes).toList();
    }

    static String path(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
