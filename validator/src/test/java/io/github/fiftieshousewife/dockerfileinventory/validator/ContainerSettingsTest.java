package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.container;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.pod;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.runAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContainerSettingsTest {

    private static Workload.Security seccomp(String profile) {
        return new Workload.Security(null, null, null, null, List.of(), profile);
    }

    @Test
    void aContainersUidWinsOverThePodsAndThePodsFillsIn() {
        Workload workload = pod(runAs(0), container("own", runAs(1001)), container("inherits", NOTHING));

        assertAll(
                () -> assertThat(ContainerSettings.runAsUser(workload.pod().containers().get(0), workload.pod()))
                        .isEqualTo(1001L),
                () -> assertThat(ContainerSettings.runAsUser(workload.pod().containers().get(1), workload.pod()))
                        .isZero());
    }

    @Test
    void aContainersSeccompProfileWinsOverThePodsAndThePodsFillsIn() {
        Workload workload = pod(seccomp("Unconfined"), container("own", seccomp("RuntimeDefault")),
                container("inherits", NOTHING));

        assertAll(
                () -> assertThat(ContainerSettings.seccomp(workload.pod().containers().get(0), workload.pod()))
                        .isEqualTo("RuntimeDefault"),
                () -> assertThat(ContainerSettings.seccomp(workload.pod().containers().get(1), workload.pod()))
                        .isEqualTo("Unconfined"));
    }
}
