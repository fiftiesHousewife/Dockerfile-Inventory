package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.real;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HostAccessTest {

    @Test
    void theHostsNamespacesAndPathsAreEachNamed() {
        assertThat(new HostAccess().problems(real("d88374c0"), V2)).extracting(Problem::message).containsExactly(
                "the pod uses the host's network", "the pod uses the host's PID namespace",
                "the pod mounts the host path /etc/origin/node", "the pod mounts the host path /etc/sysconfig",
                "the pod mounts the host path /var/run/dbus", "the pod mounts the host path /run/systemd/system");
    }

    @Test
    void theDockerSocketMountedFromTheHostIsNamed() {
        assertThat(new HostAccess().problems(real("fe15041e"), V2)).extracting(Problem::message)
                .containsExactly("the pod mounts the host path /var/run/docker.sock");
    }

    @Test
    void theHostsIpcNamespaceAndAHostPortAreNamed() {
        Workload.Container binding = new Workload.Container("app", "image", false, List.of(8080), List.of(80),
                new Workload.Probes(false, false, false), List.of(), NOTHING);
        Workload workload = new Workload("sha", "Pod", "app", null, new Workload.Pod(false, false, true, List.of(),
                NOTHING, List.of(binding)), List.of());

        assertThat(new HostAccess().problems(workload, V2)).extracting(Problem::message).containsExactly(
                "the pod uses the host's IPC namespace", "container app binds host port 80");
    }

    @Test
    void aPlainWorkloadAsksNothingOfTheHost() {
        assertThat(new HostAccess().problems(real("a185638a"), V2)).isEmpty();
    }

    @Test
    void onlyTheNamespacesThePodSharesAreNamed() {
        assertThat(HostAccess.namespaces(new Workload.Pod(false, true, false, List.of(), NOTHING, List.of())))
                .containsExactly("the pod uses the host's PID namespace");
    }
}
