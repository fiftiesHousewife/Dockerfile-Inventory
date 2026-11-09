package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.real;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class PrivilegedTest {

    @Test
    void aPrivilegedContainerIsNamedAndAPlainOneIsNot() {
        assertAll(
                () -> assertThat(new Privileged().problems(real("d88374c0"), V2))
                        .containsExactly(new Problem("container sync is privileged", Certainty.READ)),
                () -> assertThat(new Privileged().problems(real("a185638a"), V2)).isEmpty());
    }
}
