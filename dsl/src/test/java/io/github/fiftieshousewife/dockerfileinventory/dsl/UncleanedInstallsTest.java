package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UncleanedInstallsTest {

    @Test
    void anInstallWithoutItsCleanIsReportedWithTheRunText() {
        assertThat(UncleanedInstalls.of(shell("dnf install -y git", "make"))).contains("dnf install -y git && make");
        assertThat(UncleanedInstalls.of(shell("dnf install -y git", "dnf clean all"))).isEmpty();
    }

    @Test
    void runTextReadsShellRunsAndRawRunsOnly() {
        assertThat(UncleanedInstalls.runText(shell("a", "b"))).contains("a && b");
        assertThat(UncleanedInstalls.runText(new Instruction.Raw("RUN a", "copied"))).contains("RUN a");
        assertThat(UncleanedInstalls.runText(new Instruction.Raw("HEALTHCHECK NONE", "copied"))).isEmpty();
        assertThat(UncleanedInstalls.runText(new Instruction.Run(
                new RunBody.Install(new PackageInstall(PackageManager.DNF, List.of("git")))))).isEmpty();
    }

    @Test
    void uncleanedKnowsEachManagersInstallAndItsClean() {
        assertThat(UncleanedInstalls.uncleaned("microdnf -y install git")).isTrue();
        assertThat(UncleanedInstalls.uncleaned("yum install git && rm -rf /var/cache/yum")).isFalse();
        assertThat(UncleanedInstalls.uncleaned("apt-get install -y curl")).isTrue();
        assertThat(UncleanedInstalls.uncleaned("apt install curl && rm -rf /var/lib/apt/lists/*")).isFalse();
        assertThat(UncleanedInstalls.uncleaned("apk add git")).isTrue();
        assertThat(UncleanedInstalls.uncleaned("apk add git && rm -rf /var/cache/apk/*")).isFalse();
        assertThat(UncleanedInstalls.uncleaned("make install")).isFalse();
    }

    private static Instruction shell(String... commands) {
        return new Instruction.Run(new RunBody.Shell(List.of(commands)));
    }
}
