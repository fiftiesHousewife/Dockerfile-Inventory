package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RunCommandsTest {

    @Test
    void anInstallIsItsRenderedCommandsAndAShellIsItsOwn() {
        assertThat(RunCommands.of(new RunBody.Install(new PackageInstall(PackageManager.APK, List.of("git")))))
                .containsExactly("apk add --no-cache git");
        assertThat(RunCommands.of(new RunBody.Shell(List.of("a", "b")))).containsExactly("a", "b");
    }
}
