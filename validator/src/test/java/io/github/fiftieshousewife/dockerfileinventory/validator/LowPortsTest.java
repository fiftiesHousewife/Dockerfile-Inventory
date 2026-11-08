package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class LowPortsTest {

    private final LowPorts check = new LowPorts();

    @Test
    void anExposedPortBelow1024IsAProblem() {
        assertThat(check.problems(subject("0e896df4"), V2)).containsExactly(
                "port 80 is below 1024, which OpenShift's arbitrary UID cannot bind: listen above 1024 and let the "
                        + "Service expose 80");
    }

    @Test
    void anImageWithoutLowPortsHasNone() {
        assertThat(check.problems(subject("141f718e"), V2)).isEmpty();
    }

    @Test
    void aPortIsItsNumberWithoutProtocolOrRangeEndAndAVariableIsNoPort() {
        assertAll(
                () -> assertThat(LowPorts.number("443/tcp")).hasValue(443),
                () -> assertThat(LowPorts.number("21-22")).hasValue(21),
                () -> assertThat(LowPorts.number("${PORT}")).isEmpty());
    }
}
