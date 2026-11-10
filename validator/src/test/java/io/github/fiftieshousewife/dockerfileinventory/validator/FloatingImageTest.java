package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.named;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.pod;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.real;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class FloatingImageTest {

    private final FloatingImage check = new FloatingImage();

    @Test
    void aContainerNamingNoTagFloats() {
        assertThat(check.problems(real("a185638a"), V2)).containsExactly(new Problem(
                "container sleep-test-container runs rhel7, which floats with latest", Certainty.READ));
    }

    @Test
    void aContainerNamingLatestFloatsAndOneWithAFixedTagOrADigestDoesNot() {
        assertThat(check.problems(pod(NOTHING, named("a", "quay.io/app:latest", NOTHING),
                named("b", "app:1.2", NOTHING), named("c", "app@sha256:ab", NOTHING)), V2)).containsExactly(
                new Problem("container a runs quay.io/app:latest, which floats with latest", Certainty.READ));
    }

    @Test
    void aContainerNamingNoImageIsLeftAlone() {
        assertThat(check.problems(real("d88374c0"), V2)).isEmpty();
    }

    @Test
    void itBreaksInBothShippedProfiles() {
        assertAll(
                () -> assertThat(V2.severity().get(Check.D7)).isEqualTo(Severity.BREAKS),
                () -> assertThat(Fixtures.V1.severity().get(Check.D7)).isEqualTo(Severity.BREAKS));
    }
}
