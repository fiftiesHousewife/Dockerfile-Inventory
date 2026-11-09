package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

final class HostAccess implements PodCheck {

    @Override
    public Check id() {
        return Check.D5;
    }

    @Override
    public List<Problem> problems(Workload workload, Profile profile) {
        Workload.Pod pod = workload.pod();
        return Stream.of(namespaces(pod), pod.hostPaths().stream().map(path -> "the pod mounts the host path " + path),
                        pod.containers().stream().flatMap(container -> container.hostPorts().stream()
                                .map(port -> "container " + container.name() + " binds host port " + port)))
                .flatMap(messages -> messages)
                .map(message -> new Problem(message, Certainty.READ))
                .toList();
    }

    static Stream<String> namespaces(Workload.Pod pod) {
        return Stream.of(pod.hostNetwork() ? "network" : null, pod.hostPid() ? "PID namespace" : null,
                        pod.hostIpc() ? "IPC namespace" : null)
                .filter(Objects::nonNull)
                .map(namespace -> "the pod uses the host's " + namespace);
    }
}
