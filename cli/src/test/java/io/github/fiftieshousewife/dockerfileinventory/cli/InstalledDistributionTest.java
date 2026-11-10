package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class InstalledDistributionTest {

    private static final Path LAUNCHER = Path.of(System.getProperty("distribution"), "bin", "docker-inventory");

    private static Process launched(String... args) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(Stream.concat(Stream.of(LAUNCHER.toString()), Stream.of(args))
                .toList());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        return builder.redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    @Test
    void theLauncherPrintsTheVersion() throws IOException, InterruptedException {
        Process process = launched("--version");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertThat(process.waitFor()).isZero(),
                () -> assertThat(output).startsWith("docker-inventory " + Version.current()));
    }

    @Test
    void theLauncherExitsTwoForAnUnknownCommand() throws IOException, InterruptedException {
        assertThat(launched("nonsense").waitFor()).isEqualTo(2);
    }
}
