package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FinalStageTest {

    private static final Instruction.User USER = new Instruction.User("1001");

    @Test
    void finishedPutsTheGroupZeroStepBeforeUserAndOwnsUnownedCopies() {
        List<Instruction> instructions = List.of(
                new Instruction.Copy(List.of("app"), "/opt/app/", null, null),
                new Instruction.Copy(List.of("cfg"), "/etc/app/", "0:0", null),
                USER);

        assertThat(FinalStage.finished(instructions, "1001", List.of("/opt/app"))).containsExactly(
                new Instruction.Copy(List.of("app"), "/opt/app/", "1001:0", null),
                new Instruction.Copy(List.of("cfg"), "/etc/app/", "0:0", null),
                FinalStage.groupZero(List.of("/opt/app")),
                USER);
    }

    @Test
    void anOwnedCopyKeepsItsOtherOptions() {
        Instruction copy = new Instruction.Copy(List.of("run.sh"), "/bin/", null, "build", "755", true, true);

        assertThat(FinalStage.finished(copy, "1001", List.of()))
                .containsExactly(
                        new Instruction.Copy(List.of("run.sh"), "/bin/", "1001:0", "build", "755", true, true));
    }

    @Test
    void finishedAddsNoGroupZeroStepWithoutWritablePaths() {
        assertThat(FinalStage.finished(USER, "1001", List.of())).containsExactly(USER);
    }

    @Test
    void groupZeroChangesEveryWritablePathInOneRun() {
        assertThat(FinalStage.groupZero(List.of("/a", "/b"))).isEqualTo(new Instruction.Run(
                new RunBody.Shell(List.of("chgrp -R 0 /a /b", "chmod -R g=u /a /b"))));
    }
}
