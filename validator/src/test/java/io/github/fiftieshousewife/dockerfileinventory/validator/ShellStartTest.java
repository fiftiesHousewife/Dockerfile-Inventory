package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShellStartTest {

    private final ShellStart check = new ShellStart();

    @Test
    void aShellFormEntrypointIsAProblem() {
        assertThat(check.problems(subject("080b0412"), V2)).containsExactly(
                "ENTRYPOINT is in shell form, so a shell is PID 1 and does not pass on SIGTERM: OpenShift kills the "
                        + "process at the end of the grace period");
    }

    @Test
    void execFormStartsAreNot() {
        assertAll(
                () -> assertThat(check.problems(subject("141f718e"), V2)).isEmpty(),
                () -> assertThat(check.problems(subject("08bf2be5"), V2)).isEmpty());
    }

    @Test
    void aShellFormCommandCountsOnlyWithoutAnEntrypoint() {
        Subject.Command shell = new Subject.Command("SHELL", List.of("run.sh"));
        Subject.Command exec = new Subject.Command("EXEC", List.of("/init"));

        assertAll(
                () -> assertThat(ShellStart.shellForm(null, shell)).hasValue("CMD"),
                () -> assertThat(ShellStart.shellForm(exec, shell)).isEmpty());
    }
}
