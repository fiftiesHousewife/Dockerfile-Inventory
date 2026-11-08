package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FinalStageTest {

    private static final Instruction.User USER = new Instruction.User("1001");

    @Test
    void finishedPutsTheGroupZeroStepBeforeUserAndGivesUnownedCopiesToRoot() {
        List<Instruction> instructions = List.of(
                new Instruction.Copy(List.of("app"), "/opt/app/", null, null),
                new Instruction.Copy(List.of("cfg"), "/etc/app/", "0:0", null),
                USER);

        assertThat(FinalStage.finished(instructions, List.of("/opt/app"))).containsExactly(
                new Instruction.Copy(List.of("app"), "/opt/app/", "0:0", null),
                new Instruction.Copy(List.of("cfg"), "/etc/app/", "0:0", null),
                FinalStage.groupZero(List.of("/opt/app")),
                USER);
    }

    @Test
    void theGroupZeroStepGoesOnlyBeforeTheLastUserWhenTheStageSwitchesUser() {
        Instruction root = new Instruction.User("0");
        Instruction build = new Instruction.Run(new RunBody.Shell(List.of("mkdir -p /data")));
        Instruction app = new Instruction.User("1001");

        assertThat(FinalStage.finished(List.of(root, build, app), List.of("/data")))
                .containsExactly(root, build, FinalStage.groupZero(List.of("/data")), app);
    }

    @Test
    void anOwnedCopyKeepsItsOtherOptions() {
        Instruction copy = new Instruction.Copy(List.of("run.sh"), "/bin/", null, "build", "755", true, true);

        assertThat(FinalStage.finished(copy))
                .containsExactly(
                        new Instruction.Copy(List.of("run.sh"), "/bin/", "0:0", "build", "755", true, true));
    }

    @Test
    void finishedAddsNoGroupZeroStepWithoutWritablePaths() {
        assertThat(FinalStage.finished(USER)).containsExactly(USER);
    }

    @Test
    void groupZeroChangesEveryWritablePathInOneRun() {
        assertThat(FinalStage.groupZero(List.of("/a", "/b"))).isEqualTo(new Instruction.Run(
                new RunBody.Shell(List.of("chgrp -R 0 /a /b", "chmod -R g=u /a /b"))));
    }

    @Test
    void lastUserIsTheIndexOfTheLastUserOrMinusOne() {
        assertThat(List.of(FinalStage.lastUser(List.of(new Instruction.User("0"), new Instruction.Workdir("/a"), USER)),
                FinalStage.lastUser(List.of(new Instruction.Workdir("/a"))))).containsExactly(2, -1);
    }
}
