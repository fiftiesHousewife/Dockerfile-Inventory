package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class RunCommandsTest {

    @Test
    void anInstallIsItsRenderedCommandsAndAShellIsItsOwn() {
        assertAll(
                () -> assertThat(RunCommands.of(new RunBody.Install(new PackageInstall(PackageManager.APK,
                        List.of("git")))))
                        .containsExactly("apk add --no-cache git"),
                () -> assertThat(RunCommands.of(new RunBody.Shell(List.of("a", "b")))).containsExactly("a", "b"));
    }

    @Test
    void onlyAnInstallOrShellRunJoinsAndOnlyTheyHaveCommands() {
        assertAll(
                () -> assertThat(List.of(new RunBody.Shell(List.of("a")), new RunBody.Exec(List.of("a")),
                        new RunBody.Heredoc("", "EOF", true, List.of("a"))))
                        .map(RunCommands::joins).containsExactly(true, false, false),
                () -> assertThatIllegalArgumentException()
                        .isThrownBy(() -> RunCommands.of(new RunBody.Exec(List.of("a")))));
    }
}
