package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildahContainerTest {

    private final BuildahContainer container = new BuildahContainer(Path.of("/tmp/compare/src"),
            Path.of("/tmp/compare/work"), "arm64");

    @Test
    void aHostPathIsItsPlaceUnderSrcOrWorkInsideTheContainer() {
        assertAll(
                () -> assertThat(container.inside(Path.of("/tmp/compare/src/o/r/abc/Dockerfile")))
                        .isEqualTo("/src/o/r/abc/Dockerfile"),
                () -> assertThat(container.inside(Path.of("/tmp/compare/work/abc/regenerated.Dockerfile")))
                        .isEqualTo("/work/abc/regenerated.Dockerfile"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> container.inside(Path.of("/etc/hosts"))));
    }

    @Test
    void dockersArchitectureIsNamedAsAPlatformNamesIt() {
        assertAll(
                () -> assertThat(BuildahContainer.architecture("x86_64\n")).isEqualTo("amd64"),
                () -> assertThat(BuildahContainer.architecture("aarch64")).isEqualTo("arm64"),
                () -> assertThatIllegalStateException().isThrownBy(() -> BuildahContainer.architecture(" \n"))
                        .withMessageContaining("Docker"));
    }
}
