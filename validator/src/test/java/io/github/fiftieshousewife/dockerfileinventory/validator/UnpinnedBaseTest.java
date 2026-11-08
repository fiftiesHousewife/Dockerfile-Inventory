package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnpinnedBaseTest {

    private final UnpinnedBase check = new UnpinnedBase();

    @Test
    void aBaseByTagAloneIsAProblem() {
        assertThat(check.problems(subject("08bf2be5"), V2)).containsExactly(
                "the base registry.access.redhat.com/ubi9/ubi-minimal:9.8-1788166357 is not pinned by digest, so a "
                        + "rebuild may take a different image");
    }

    @Test
    void aBaseWithNoTagSaysItFloats() {
        assertThat(check.problems(subject("2fc5b392"), V2)).containsExactly(
                "the base cassandra has no tag or digest, so it floats with latest");
    }

    @Test
    void aBasePinnedByDigestIsNot() {
        assertThat(check.problems(subject("01c98292"), V2)).isEmpty();
    }
}
