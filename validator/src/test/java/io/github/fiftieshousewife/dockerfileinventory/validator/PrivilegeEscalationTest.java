package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.container;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.pod;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.real;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class PrivilegeEscalationTest {

    @Test
    void onlyAnExplicitTrueIsNamed() {
        Workload escalating = pod(NOTHING, container("app", new Workload.Security(null, null, null, true, List.of(),
                null)));

        assertAll(
                () -> assertThat(new PrivilegeEscalation().problems(escalating, V2)).containsExactly(
                        new Problem("container app allows privilege escalation", Certainty.READ)),
                () -> assertThat(new PrivilegeEscalation().problems(real("a185638a"), V2)).isEmpty());
    }
}
