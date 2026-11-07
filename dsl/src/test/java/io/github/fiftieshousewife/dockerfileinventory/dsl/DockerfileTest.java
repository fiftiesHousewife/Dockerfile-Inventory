package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class DockerfileTest {

    @Test
    void aDockerfileNeedsAtLeastOneStage() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Dockerfile(List.of(), List.of()));
    }
}
