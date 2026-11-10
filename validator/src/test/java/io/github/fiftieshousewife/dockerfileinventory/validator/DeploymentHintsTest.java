package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeploymentHintsTest {

    private final DeploymentHints check = new DeploymentHints();

    @Test
    void aHealthcheckIsIgnoredAndNeedsAProbe() {
        assertThat(check.problems(subject("2fc5b392"), V2)).containsExactly(
                "OpenShift ignores HEALTHCHECK: declare a liveness or readiness probe in the deployment");
    }

    @Test
    void aVolumeIsAPlainDirectoryAndNeedsAVolumeInTheDeployment() {
        assertThat(check.problems(subject("141f718e"), V2)).containsExactly(
                "VOLUME /var/lib/pgsql/data is a plain directory in the container on OpenShift: declare a volume in "
                        + "the deployment for data that must outlive it");
    }

    @Test
    void anImageWithNeitherHasNone() {
        assertThat(check.problems(subject("08bf2be5"), V2)).isEmpty();
    }

    private static Workload.Container deployed(Workload.Probes probes, String... mounts) {
        return new Workload.Container("c", "image:1", false, List.of(), List.of(), probes, List.of(mounts),
                WorkloadTest.NOTHING);
    }

    private static final Workload.Probes NO_PROBES = new Workload.Probes(false, false, false);

    @Test
    void aLivenessOrReadinessProbeAnswersAHealthcheck() {
        assertThat(DeploymentHints.unanswered(subject("2fc5b392"), deployed(new Workload.Probes(false, true, false))))
                .isEmpty();
    }

    @Test
    void aStartupProbeAloneDoesNotAnswerAHealthcheck() {
        assertThat(DeploymentHints.unanswered(subject("2fc5b392"), deployed(new Workload.Probes(false, false, true))))
                .hasSize(1);
    }

    @Test
    void aMountAtTheVolumesPathAnswersItWithOrWithoutATrailingSlash() {
        assertThat(DeploymentHints.unanswered(subject("12baa7e1"), deployed(NO_PROBES, "/etc/mts/")))
                .containsExactly("VOLUME /etc/fedmsg.d is a plain directory in the container on OpenShift: declare a "
                        + "volume in the deployment for data that must outlive it");
    }

    @Test
    void aPathLosesItsTrailingSlashUnlessItIsTheRoot() {
        assertAll(
                () -> assertThat(DeploymentHints.path("/data/")).isEqualTo("/data"),
                () -> assertThat(DeploymentHints.path("/")).isEqualTo("/"));
    }
}
