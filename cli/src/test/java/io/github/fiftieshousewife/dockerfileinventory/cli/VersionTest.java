package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void theVersionIsTheBuildsWrittenIntoTheJar() {
        assertThat(Version.current()).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }
}
