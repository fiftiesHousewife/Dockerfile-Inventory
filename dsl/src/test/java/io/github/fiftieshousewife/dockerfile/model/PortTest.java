package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class PortTest {

    @Test
    void aPortARangeAVariableAndAProtocolAreAccepted() {
        assertThat(List.of("8080", "53/udp", "8000-8010", "8000-8010/tcp", "$PORT", "${PORT}/sctp", "1", "65535"))
                .map(spec -> new Port(spec).spec())
                .containsExactly("8080", "53/udp", "8000-8010", "8000-8010/tcp", "$PORT", "${PORT}/sctp", "1",
                        "65535");
    }

    @Test
    void aPortOutOfRangeABackwardRangeOrAnUnknownProtocolIsRefused() {
        assertThat(List.of("0", "65536", "9000-8000", "80/http", "", "80 443", "abc"))
                .allSatisfy(spec -> assertThatIllegalArgumentException().isThrownBy(() -> new Port(spec))
                        .withMessageContaining("port"));
    }

    @Test
    void aNumberIsAPort() {
        assertThat(Port.of(8080)).isEqualTo(new Port("8080"));
    }

    @Test
    void aRangeIsInsideOneTo65535AndNotBackward() {
        assertThat(List.of(Port.inRange(null, null), Port.inRange("80", null), Port.inRange("80", "80"),
                Port.inRange("0", null), Port.inRange("90", "80"), Port.inRange("1", "65536")))
                .containsExactly(true, true, true, false, false, false);
    }
}
