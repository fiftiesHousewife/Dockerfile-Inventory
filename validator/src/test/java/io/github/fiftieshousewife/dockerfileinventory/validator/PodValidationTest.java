package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.container;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.pod;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.real;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.runAs;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PodValidationTest {

    @Test
    void everyPodCheckRunsInTheOrderOfItsId() {
        assertThat(PodValidation.CHECKS).extracting(PodCheck::id)
                .containsExactly(Check.D1, Check.D2, Check.D3, Check.D4, Check.D5, Check.D6);
    }

    @Test
    void eachProblemBecomesARefusedFindingOnTheWorkloadsFile() {
        assertThat(PodValidation.findings(real("fe15041e"), V2)).containsExactly(
                new Finding(Check.D1, Severity.REFUSED, Certainty.READ, "fe15041e95910e1a1179dbb4c96a7c51140a7966",
                        "container image-inspector is privileged"),
                new Finding(Check.D5, Severity.REFUSED, Certainty.READ, "fe15041e95910e1a1179dbb4c96a7c51140a7966",
                        "the pod mounts the host path /var/run/docker.sock"));
    }

    @Test
    void aCheckTheProfileTurnsOffFindsNothing() {
        Workload escalating = pod(NOTHING, container("app", new Workload.Security(null, null, null, true, List.of(),
                null)));

        assertThat(PodValidation.findings(escalating, Profile.shipped("openshift-4-restricted"))).isEmpty();
    }

    @Test
    void aFindingKeepsItsProblemsCertainty() {
        assertThat(PodValidation.findings(pod(NOTHING, container("app", runAs(1001))), V2))
                .extracting(Finding::certainty).containsExactly(Certainty.INFERRED);
    }
}
