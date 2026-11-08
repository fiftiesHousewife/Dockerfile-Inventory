package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;

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
}
