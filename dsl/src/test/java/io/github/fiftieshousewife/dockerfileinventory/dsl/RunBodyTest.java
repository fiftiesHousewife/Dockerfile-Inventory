package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunBodyTest {

    @Test
    void aShellRunNeedsAtLeastOneCommandAndCopiesThem() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RunBody.Shell(List.of()));
        assertThat(new RunBody.Shell(new ArrayList<>(List.of("make"))).commands()).isUnmodifiable();
    }
}
