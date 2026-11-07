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

    @Test
    void anExecRunNeedsAnArgumentAndAHeredocADelimiterAndBothCopyTheirLists() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RunBody.Exec(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RunBody.Heredoc("", " ", true, List.of("make")));
        assertThat(new RunBody.Exec(new ArrayList<>(List.of("make"))).arguments()).isUnmodifiable();
        assertThat(new RunBody.Heredoc("", "EOF", false, new ArrayList<>(List.of("make"))).lines()).isUnmodifiable();
    }
}
