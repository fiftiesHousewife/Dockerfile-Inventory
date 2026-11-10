package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildahContainerTest {

    @TempDir
    Path logs;

    @Test
    void dockersArchitectureIsNamedAsAPlatformNamesIt() {
        assertAll(
                () -> assertThat(BuildahContainer.architecture("x86_64\n")).isEqualTo("amd64"),
                () -> assertThat(BuildahContainer.architecture("aarch64")).isEqualTo("arm64"),
                () -> assertThatIllegalStateException().isThrownBy(() -> BuildahContainer.architecture(" \n"))
                        .withMessageContaining("Docker"));
    }

    @Test
    void theContainersArchitectureIsItsCommands() {
        assertThat(new BuildahContainer(new BuildahCommands(logs, logs, "arm64", 10)).arch())
                .isEqualTo("arm64");
    }

    @Test
    void theBuildTimeoutIsTenMinutesUnlessSet() {
        assertAll(
                () -> assertThat(BuildahContainer.timeoutMinutes(Map.of())).isEqualTo(10),
                () -> assertThat(BuildahContainer.timeoutMinutes(Map.of("BUILD_TIMEOUT", "30"))).isEqualTo(30));
    }

    @Test
    void aBuildTimeoutThatIsNotAPositiveWholeNumberIsRefusedByName() {
        assertThatIllegalStateException().isThrownBy(() -> BuildahContainer.timeoutMinutes(
                Map.of("BUILD_TIMEOUT", "ten"))).withMessage(
                "BUILD_TIMEOUT must be a positive whole number of minutes, not ten");
    }

    private BuildOutcome retried(List<String> attempts, List<BuildOutcome> outcomes, List<Integer> made) {
        Path log = logs.resolve("original-build-directory.log");
        return BuildahContainer.retried(() -> {
            int attempt = made.size();
            made.add(attempt);
            RunFiles.writeFile(log, attempts.get(attempt));
            return outcomes.get(attempt);
        }, log);
    }

    @Test
    void aBuildWhoseDownloadFailedIsTriedOnceMoreKeepingTheFirstLog() {
        List<Integer> made = new ArrayList<>();

        BuildOutcome outcome = retried(List.of("TLS: unspecified error", "built"),
                List.of(BuildOutcome.FAILED, BuildOutcome.BUILT), made);

        assertAll(
                () -> assertThat(outcome).isEqualTo(BuildOutcome.BUILT),
                () -> assertThat(made).hasSize(2),
                () -> assertThat(logs.resolve("original-build-directory.first.log")).hasContent("TLS: unspecified error"));
    }

    @Test
    void aBuildThatFailedOnItsOwnOrWhoseRetryFailedIsNotTriedAgain() {
        List<Integer> once = new ArrayList<>();
        List<Integer> twice = new ArrayList<>();

        assertAll(
                () -> assertThat(retried(List.of("error: expected ';'"), List.of(BuildOutcome.FAILED), once))
                        .isEqualTo(BuildOutcome.FAILED),
                () -> assertThat(once).hasSize(1),
                () -> assertThat(retried(List.of("TLS: unspecified error", "TLS: unspecified error"),
                        List.of(BuildOutcome.FAILED, BuildOutcome.FAILED), twice)).isEqualTo(BuildOutcome.FAILED),
                () -> assertThat(twice).hasSize(2));
    }
}
