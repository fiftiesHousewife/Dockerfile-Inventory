package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoundTripTest {

    @Test
    void theRealPython311DockerfileConvertsAndPasses() throws IOException {
        String original = Files.readString(Path.of("../facts/src/test/resources/fixtures/dockerfiles/python-311.Dockerfile"));

        RoundTrip result = RoundTrip.check(original, Convert.of(Parsed.of(original)).model());

        assertThat(result.difference()).isNull();
        assertThat(result.passed()).isTrue();
    }
}
