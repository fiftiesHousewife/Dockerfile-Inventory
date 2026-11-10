package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FloatingBaseTest {

    private final FloatingBase check = new FloatingBase();

    @Test
    void aBaseWithNoTagFloats() {
        assertThat(check.problems(subject("2fc5b392"), V2)).containsExactly(
                "the base cassandra has no tag or digest, so it floats with latest");
    }

    @Test
    void aBaseTaggedLatestFloats() {
        assertThat(check.problems(subject("6cc3382e"), V2)).containsExactly(
                "the base alpine:latest has no digest, so it floats with latest");
    }

    @Test
    void aBaseWithAFixedTagOrADigestDoesNot() {
        assertThat(check.problems(subject("08bf2be5"), V2)).isEmpty();
    }

    @Test
    void itBreaksInBothShippedProfiles() {
        assertThat(Fixtures.V1.severity().get(Check.P11)).isEqualTo(V2.severity().get(Check.P11))
                .isEqualTo(Severity.BREAKS);
    }
}
