package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.container;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.pod;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnconfinedTest {

    private static Workload.Security seccomp(String profile) {
        return new Workload.Security(null, null, null, null, List.of(), profile);
    }

    @Test
    void anUnconfinedPodNamesEachContainerThatKeepsIt() {
        assertThat(new Unconfined().problems(pod(seccomp("Unconfined"), container("app", NOTHING),
                container("own", seccomp("RuntimeDefault"))), V2)).containsExactly(
                new Problem("container app runs without a seccomp profile (Unconfined)", Certainty.READ));
    }
}
