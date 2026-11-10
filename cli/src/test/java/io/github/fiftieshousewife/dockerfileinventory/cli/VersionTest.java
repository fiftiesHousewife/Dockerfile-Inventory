package io.github.fiftieshousewife.dockerfileinventory.cli;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void theVersionIsTheBuildsWrittenIntoTheJar() {
        assertThat(Version.current()).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }

    @Test
    void aVersionFileThatCannotBeReadIsAnError() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("gone");
            }
        };

        assertThatThrownBy(() -> Version.read(broken)).isInstanceOf(UncheckedIOException.class);
    }
}
