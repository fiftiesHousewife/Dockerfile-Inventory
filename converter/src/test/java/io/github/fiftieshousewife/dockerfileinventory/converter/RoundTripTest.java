package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Stage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundTripTest {

    @Test
    void theRealPython311DockerfileConvertsAndPasses() throws IOException {
        String original = Files.readString(Path.of("../facts/src/test/resources/fixtures/dockerfiles/python-311.Dockerfile"));

        RoundTrip result = RoundTrip.check(original, Convert.of(Parsed.of(original)).model());

        assertThat(result.difference()).isNull();
        assertThat(result.passed()).isTrue();
    }

    @Test
    void aConversionThatDropsOrChangesAnInstructionFailsNamingTheStep() {
        String original = "FROM ubi9\nUSER 1001\nRUN make\n";
        Dockerfile converted = Convert.of(Parsed.of(original)).model();
        Stage stage = converted.stages().getFirst();
        Dockerfile dropped = new Dockerfile(List.of(), List.of(new Stage(null, stage.base(), null,
                List.of(stage.instructions().getFirst()))));
        Dockerfile changed = new Dockerfile(List.of(), List.of(new Stage(null, stage.base(), null,
                List.of(new Instruction.User("1002"), stage.instructions().get(1)))));

        assertThat(RoundTrip.check(original, dropped).difference()).contains("step 2").contains("RUN make");
        assertThat(RoundTrip.check(original, changed).difference()).contains("step 1").contains("1002");
    }

    @Test
    void flagAndPackageOrderInsideAnInstallDoNotCount() {
        String original = "FROM ubi9\nRUN microdnf install tar git -y && microdnf clean all\n";

        assertThat(RoundTrip.check(original, Convert.of(Parsed.of(original)).model()).passed()).isTrue();
    }
}
