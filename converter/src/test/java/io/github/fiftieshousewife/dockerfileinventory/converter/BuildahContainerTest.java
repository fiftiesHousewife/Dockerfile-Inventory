package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildahContainerTest {

    private final BuildahContainer container = new BuildahContainer(Path.of("/tmp/compare/src"),
            Path.of("/tmp/compare/work"), "arm64", 10);

    @Test
    void aHostPathIsItsPlaceUnderSrcOrWorkInsideTheContainer() {
        assertAll(
                () -> assertThat(container.inside(Path.of("/tmp/compare/src/o/r/abc/Dockerfile")))
                        .isEqualTo("/src/o/r/abc/Dockerfile"),
                () -> assertThat(container.inside(Path.of("/tmp/compare/work/abc/regenerated.Dockerfile")))
                        .isEqualTo("/work/abc/regenerated.Dockerfile"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> container.inside(Path.of("/etc/hosts"))));
    }

    @Test
    void dockersArchitectureIsNamedAsAPlatformNamesIt() {
        assertAll(
                () -> assertThat(BuildahContainer.architecture("x86_64\n")).isEqualTo("amd64"),
                () -> assertThat(BuildahContainer.architecture("aarch64")).isEqualTo("arm64"),
                () -> assertThatIllegalStateException().isThrownBy(() -> BuildahContainer.architecture(" \n"))
                        .withMessageContaining("Docker"));
    }

    @Test
    void aBuildGivesEachSecretTheEmptyFileAndNamesTheDockerfileAndContextInsideTheContainer() {
        assertThat(container.buildCommand(Path.of("/tmp/compare/src/o/r/abc/Dockerfile"),
                Path.of("/tmp/compare/src/o/r/abc"), "compare/x:original", List.of("downloads_url")))
                .containsExactly("docker", "exec", "inventory-compare", "timeout", "-k", "30", "600", "buildah", "build",
                        "-q",
                        "--format",
                        "docker", "--layers=false", "--secret", "id=downloads_url,src=/work/empty-secret", "-f",
                        "/src/o/r/abc/Dockerfile", "-t", "compare/x:original", "/src/o/r/abc");
    }

    @Test
    void theContainerMountsTheRegistryLoginAndMirrorsWhenTheyAreSet() {
        assertThat(container.runCommand(Map.of("REGISTRY_AUTH_FILE", "/home/me/auth.json",
                "REGISTRIES_CONF", "/home/me/registries.conf")))
                .containsSubsequence("-v", "/home/me/auth.json:/auth.json:ro", "-e", "REGISTRY_AUTH_FILE=/auth.json")
                .containsSubsequence("-v", "/home/me/registries.conf:/etc/containers/registries.conf:ro")
                .endsWith(BuildahContainer.IMAGE, "sleep", "infinity");
    }

    @Test
    void withoutThemTheContainerMountsNeither() {
        assertThat(container.runCommand(Map.of())).noneMatch(argument -> argument.contains("auth.json")
                || argument.contains("registries.conf"));
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

    @Test
    void atTheRunsEndWhatAKilledBuildLeftIsCleared() {
        assertThat(container.clearingCommands()).containsExactly(
                List.of("docker", "exec", "inventory-compare", "buildah", "rm", "--all"),
                List.of("docker", "exec", "inventory-compare", "buildah", "rmi", "--prune"));
    }

    @Test
    void removingAnImageForcesItsRemovalByTag() {
        assertThat(container.removeCommand("compare/x:original")).containsExactly(
                "docker", "exec", "inventory-compare", "buildah", "rmi", "-f", "compare/x:original");
    }

    @TempDir
    Path logs;

    private BuildOutcome retried(List<String> attempts, List<BuildOutcome> outcomes, List<Integer> made) {
        Path log = logs.resolve("original-build-directory.log");
        return container.retried(() -> {
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
