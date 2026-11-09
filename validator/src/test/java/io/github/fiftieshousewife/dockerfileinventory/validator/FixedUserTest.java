package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.container;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.pod;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.real;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.runAs;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FixedUserTest {

    @Test
    void runningAsRootIsReadAsRefused() {
        assertThat(new FixedUser().problems(real("d88374c0"), V2))
                .containsExactly(new Problem("container sync runs as UID 0", Certainty.READ));
    }

    @Test
    void anyOtherFixedUidIsInferredSinceTheNamespaceRangeIsUnknown() {
        assertThat(new FixedUser().problems(pod(NOTHING, container("app", runAs(1001))), V2))
                .containsExactly(new Problem("container app runs as UID 1001, admitted only if the namespace's "
                        + "range holds it", Certainty.INFERRED));
    }

    @Test
    void aContainersUidOverridesThePods() {
        assertThat(new FixedUser().problems(pod(runAs(0), container("app", runAs(1001)), container("side", NOTHING)),
                V2)).extracting(Problem::message).containsExactly(
                "container app runs as UID 1001, admitted only if the namespace's range holds it",
                "container side runs as UID 0");
    }

    @Test
    void noUidAnywhereLeavesTheSccToAssignOne() {
        assertThat(new FixedUser().problems(real("a185638a"), V2)).isEmpty();
    }
}
