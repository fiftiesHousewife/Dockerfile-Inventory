package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessesTest {

    @Test
    void outputIsWhatACommandPrintsAndAFailingCommandIsRefusedWithItsCommandLine() {
        assertAll(
                () -> assertThat(Processes.output(List.of("echo", "hello"))).isEqualTo("hello\n"),
                () -> assertThatIllegalStateException().isThrownBy(() -> Processes.output(List.of("false")))
                        .withMessageContaining("false"));
    }

    @Test
    void aLoggedCommandWritesItsOutputToTheLogAndSaysWhetherItSucceeded(@TempDir Path logs) {
        assertAll(
                () -> assertThat(Processes.logged(List.of("echo", "built"), logs.resolve("a.log"))).isTrue(),
                () -> assertThat(logs.resolve("a.log")).hasContent("built"),
                () -> assertThat(Processes.logged(List.of("false"), logs.resolve("b.log"))).isFalse());
    }

    @Test
    void aStreamedCommandsOutputIsReadAsItComes() {
        String read = Processes.streamed(List.of("echo", "tar"),
                in -> new String(in.readAllBytes(), StandardCharsets.UTF_8));

        assertThat(read).isEqualTo("tar\n");
    }
}
