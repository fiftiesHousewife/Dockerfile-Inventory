package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class UserSwitchTest {

    private final UserSwitch check = new UserSwitch();

    @Test
    void sudoInstalledInTheFinalImageIsAProblem() {
        assertThat(check.problems(subject("03cab6f0"), V2)).containsExactly(
                "sudo is installed in the final image; OpenShift's arbitrary UID cannot use it to switch user or "
                        + "raise privilege");
    }

    @Test
    void anImageWithoutSudoOrASwitchingStartHasNone() {
        assertThat(check.problems(subject("08bf2be5"), V2)).isEmpty();
    }

    @Test
    void aStartCommandSwitchesUserWhenItRunsSudoSuGosuOrSuExec() {
        assertAll(
                () -> assertThat(UserSwitch.switching(List.of("gosu", "postgres", "postgres"))).hasValue("gosu"),
                () -> assertThat(UserSwitch.switching(List.of("sh", "-c", "su -s /bin/sh app -c ./run")))
                        .hasValue("su"),
                () -> assertThat(UserSwitch.switching(List.of("/usr/local/bin/su-exec", "app"))).hasValue("su-exec"),
                () -> assertThat(UserSwitch.switching(List.of("/usr/bin/supervisord"))).isEmpty());
    }
}
