package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckNamesTest {

    @Test
    void everyCheckHasAPlainName() {
        assertThat(List.of("P1", "P6", "P11", "D7", "S5", "R3", "H9", "I2"))
                .allMatch(check -> !CheckNames.of(check).equals(check));
    }
}
