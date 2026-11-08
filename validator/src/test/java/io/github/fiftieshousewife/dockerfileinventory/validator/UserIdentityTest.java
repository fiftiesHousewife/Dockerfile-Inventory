package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class UserIdentityTest {

    private final UserIdentity check = new UserIdentity();

    @Test
    void aStartCommandAskingWhoItIsIsAProblem() {
        assertThat(check.problems(subject("6cc3382e"), V2)).containsExactly(
                "the start command reads whoami, but OpenShift's arbitrary UID may have no name or home in "
                        + "/etc/passwd");
    }

    @Test
    void aStartCommandThatDoesNotIsNot() {
        assertThat(check.problems(subject("141f718e"), V2)).isEmpty();
    }

    @Test
    void theUsersNameOrHomeIsReadThroughWhoamiIdUserHomeOrATilde() {
        assertAll(
                () -> assertThat(UserIdentity.reads(List.of("sh", "-c", "cd $HOME && ./run"))).hasValue("$HOME"),
                () -> assertThat(UserIdentity.reads(List.of("sh", "-c", "exec ${USER}-agent"))).hasValue("${USER}"),
                () -> assertThat(UserIdentity.reads(List.of("sh", "-c", "~/bin/start"))).hasValue("~"),
                () -> assertThat(UserIdentity.reads(List.of("id", "-un"))).hasValue("id -un"),
                () -> assertThat(UserIdentity.reads(List.of("/usr/bin/idle", "--home=/srv"))).isEmpty());
    }
}
