package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TalliesTest {

    @Test
    void managersCountTheLocationsOfEveryDockerfileCallingEach() {
        Estate estate = new Estate(List.of(PassesTest.dockerfile("a", "t", "s", 2),
                PassesTest.dockerfile("b", "t", "s", 3)), List.of(), List.of(), Map.of());

        assertThat(Tallies.managers(estate)).isEqualTo(Map.of("microdnf", 5));
    }

    @Test
    void aDockerfilesEffectiveUserReplacesTheUserAsWritten() {
        Estate estate = new Estate(List.of(PassesTest.dockerfile("a", "t", "s", 2),
                PassesTest.dockerfile("b", "t", "s", 3)), List.of(), List.of(), Map.of("b", UserKind.NUMERIC));

        assertThat(Tallies.users(estate)).isEqualTo(Map.of("ROOT", 2, "NUMERIC", 3));
    }
}
