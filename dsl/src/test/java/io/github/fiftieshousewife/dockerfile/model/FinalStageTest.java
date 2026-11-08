package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FinalStageTest {

    private static final User USER = new User("1001");

    @Test
    void finishedPutsTheGroupZeroStepBeforeUserAndGivesUnownedCopiesToRoot() {
        List<Instruction> instructions = List.of(
                new Copy(List.of("app"), "/opt/app/", null, null),
                new Copy(List.of("cfg"), "/etc/app/", "0:0", null),
                USER);

        assertThat(FinalStage.finished(instructions, List.of("/opt/app"))).containsExactly(
                new Copy(List.of("app"), "/opt/app/", "0:0", null),
                new Copy(List.of("cfg"), "/etc/app/", "0:0", null),
                FinalStage.groupZero(List.of("/opt/app")),
                USER);
    }

    @Test
    void theGroupZeroStepGoesOnlyBeforeTheLastUserWhenTheStageSwitchesUser() {
        Instruction root = new User("0");
        Instruction build = new Run(new RunBody.Shell(List.of("mkdir -p /data")));
        Instruction app = new User("1001");

        assertThat(FinalStage.finished(List.of(root, build, app), List.of("/data")))
                .containsExactly(root, build, FinalStage.groupZero(List.of("/data")), app);
    }

    @Test
    void anOwnedCopyKeepsItsOtherOptions() {
        Instruction copy = new Copy(List.of("run.sh"), "/bin/", null, "build", "755", true, true);

        assertThat(FinalStage.finished(copy))
                .containsExactly(
                        new Copy(List.of("run.sh"), "/bin/", "0:0", "build", "755", true, true));
    }

    @Test
    void finishedAddsNoGroupZeroStepWithoutWritablePaths() {
        assertThat(FinalStage.finished(USER)).containsExactly(USER);
    }

    @Test
    void groupZeroChangesEveryWritablePathInOneRun() {
        assertThat(FinalStage.groupZero(List.of("/a", "/b"))).isEqualTo(new Run(
                new RunBody.Shell(List.of("chgrp -R 0 /a /b", "chmod -R g=u /a /b"))));
    }

    @Test
    void lastUserIsTheIndexOfTheLastUserOrMinusOne() {
        assertThat(List.of(FinalStage.lastUser(List.of(new User("0"), new Workdir("/a"), USER)),
                FinalStage.lastUser(List.of(new Workdir("/a"))))).containsExactly(2, -1);
    }
}
