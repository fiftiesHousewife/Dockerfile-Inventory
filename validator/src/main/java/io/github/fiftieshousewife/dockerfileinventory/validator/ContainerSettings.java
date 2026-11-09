package io.github.fiftieshousewife.dockerfileinventory.validator;

final class ContainerSettings {

    private ContainerSettings() {
    }

    static Long runAsUser(Workload.Container container, Workload.Pod pod) {
        return container.security().runAsUser() != null ? container.security().runAsUser()
                : pod.security().runAsUser();
    }

    static String seccomp(Workload.Container container, Workload.Pod pod) {
        return container.security().seccomp() != null ? container.security().seccomp() : pod.security().seccomp();
    }
}
