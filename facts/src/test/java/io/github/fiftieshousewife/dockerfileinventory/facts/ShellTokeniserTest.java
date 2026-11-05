package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShellTokeniserTest {

    @Test
    void splitsOneCommandIntoWords() {
        assertThat(ShellTokeniser.parse("microdnf install -y git"))
                .contains(List.of(List.of("microdnf", "install", "-y", "git")));
    }

    @Test
    void separatesCommandsOnAndOrAndSemicolon() {
        assertThat(ShellTokeniser.parse("microdnf install -y git && microdnf clean all; rm -rf /tmp/x || true"))
                .contains(List.of(
                        List.of("microdnf", "install", "-y", "git"),
                        List.of("microdnf", "clean", "all"),
                        List.of("rm", "-rf", "/tmp/x"),
                        List.of("true")));
    }
}
