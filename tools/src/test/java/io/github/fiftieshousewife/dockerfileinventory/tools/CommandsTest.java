package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandsTest {

    @Test
    void aProcessGivesItsExitAndItsOutputWithWhatItWasGivenOnStdin() {
        Commands processes = Commands.processes();

        assertAll(
                () -> assertThat(processes.run(List.of("cat"), "piped")).isEqualTo(new Commands.Result(0, "piped")),
                () -> assertThat(processes.run("sh", "-c", "echo out; exit 3").ok()).isFalse());
    }

    @Test
    void aCommandThatCannotStartIsRefused() {
        assertThatThrownBy(() -> Commands.processes().run(List.of("no-such-command-for-the-test"), null))
                .isInstanceOf(UncheckedIOException.class);
    }
}
