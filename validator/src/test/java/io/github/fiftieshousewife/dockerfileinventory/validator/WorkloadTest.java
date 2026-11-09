package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkloadTest {

    static final List<Workload> REAL = Workload.read(Path.of("src/test/resources/fixtures/workloads.jsonl"));

    static final Workload.Security NOTHING = new Workload.Security(null, null, null, null, List.of(), null);

    static Workload.Security runAs(long uid) {
        return new Workload.Security(null, uid, null, null, List.of(), null);
    }

    static Workload.Container container(String name, Workload.Security security) {
        return new Workload.Container(name, "image", false, List.of(), List.of(),
                new Workload.Probes(false, false, false), List.of(), security);
    }

    static Workload pod(Workload.Security security, Workload.Container... containers) {
        return new Workload("sha", "Deployment", "app", null, new Workload.Pod(false, false, false, List.of(),
                security, List.of(containers)), List.of());
    }

    static Workload real(String shaPrefix) {
        return REAL.stream().filter(w -> w.blobSha().startsWith(shaPrefix)).findFirst().orElseThrow();
    }

    @Test
    void eachLineIsOneWorkloadWithItsPodAndLocations() {
        Workload sync = real("d88374c0");

        assertAll(
                () -> assertThat(REAL).hasSize(3),
                () -> assertThat(sync.kind()).isEqualTo("DaemonSet"),
                () -> assertThat(sync.name()).isEqualTo("sync"),
                () -> assertThat(sync.pod().hostNetwork()).isTrue(),
                () -> assertThat(sync.pod().containers().getFirst().security().privileged()).isTrue(),
                () -> assertThat(sync.pod().containers().getFirst().security().runAsUser()).isZero(),
                () -> assertThat(sync.locations()).containsExactly(new Location("openshift/openshift-tools",
                        "ansible/roles/openshift_node_group/files/sync.yaml")));
    }
}
