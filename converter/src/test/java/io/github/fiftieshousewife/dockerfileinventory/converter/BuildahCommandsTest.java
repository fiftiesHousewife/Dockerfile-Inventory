package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildahCommandsTest {

    private final BuildahCommands commands = new BuildahCommands(Path.of("/tmp/compare/src"),
            Path.of("/tmp/compare/work"), "arm64", 10);

    @Test
    void aHostPathIsItsPlaceUnderSrcOrWorkInsideTheContainer() {
        assertAll(
                () -> assertThat(commands.inside(Path.of("/tmp/compare/src/o/r/abc/Dockerfile")))
                        .isEqualTo("/src/o/r/abc/Dockerfile"),
                () -> assertThat(commands.inside(Path.of("/tmp/compare/work/abc/regenerated.Dockerfile")))
                        .isEqualTo("/work/abc/regenerated.Dockerfile"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> commands.inside(Path.of("/etc/hosts"))));
    }

    @Test
    void aBuildGivesEachSecretTheEmptyFileAndNamesTheDockerfileAndContextInsideTheContainer() {
        assertThat(commands.build(Path.of("/tmp/compare/src/o/r/abc/Dockerfile"),
                Path.of("/tmp/compare/src/o/r/abc"), "compare/x:original", List.of("downloads_url")))
                .containsExactly("docker", "exec", "inventory-compare", "timeout", "-k", "30", "600", "buildah", "build",
                        "-q", "--format", "docker", "--layers=false", "--secret",
                        "id=downloads_url,src=/work/empty-secret", "-f", "/src/o/r/abc/Dockerfile", "-t",
                        "compare/x:original", "/src/o/r/abc");
    }

    @Test
    void theContainerRunsOnDockersOwnPlatformWithTheSourcesWorkAndStoreMounted() {
        assertThat(commands.start(Map.of())).containsExactly("docker", "run", "-d", "--rm", "--privileged",
                "--platform", "linux/arm64", "--name", "inventory-compare", "-e", "STORAGE_DRIVER=vfs", "-v",
                "/tmp/compare/src:/src", "-v", "/tmp/compare/work:/work", "-v",
                "inventory-compare-buildah:/var/lib/containers", BuildahCommands.IMAGE, "sleep", "infinity");
    }

    @Test
    void theContainerMountsTheRegistryLoginAndMirrorsWhenTheyAreSet() {
        assertThat(commands.start(Map.of("REGISTRY_AUTH_FILE", "/home/me/auth.json",
                "REGISTRIES_CONF", "/home/me/registries.conf")))
                .containsSubsequence("-v", "/home/me/auth.json:/auth.json:ro", "-e", "REGISTRY_AUTH_FILE=/auth.json")
                .containsSubsequence("-v", "/home/me/registries.conf:/etc/containers/registries.conf:ro")
                .endsWith(BuildahCommands.IMAGE, "sleep", "infinity");
    }

    @Test
    void withoutThemTheContainerMountsNeither() {
        assertThat(commands.start(Map.of())).noneMatch(argument -> argument.contains("auth.json")
                || argument.contains("registries.conf"));
    }

    @Test
    void aSettingIsTheAbsolutePathItNamesAndBlankIsUnset() {
        assertAll(
                () -> assertThat(BuildahCommands.setting(Map.of("A", "auth.json"), "A"))
                        .contains(Path.of("auth.json").toAbsolutePath().toString()),
                () -> assertThat(BuildahCommands.setting(Map.of("A", " "), "A")).isEmpty(),
                () -> assertThat(BuildahCommands.setting(Map.of(), "A")).isEmpty());
    }

    @Test
    void stoppingTheContainerForcesItsRemoval() {
        assertThat(BuildahCommands.stop()).containsExactly("docker", "rm", "-f", "inventory-compare");
    }

    @Test
    void atTheRunsEndWhatAKilledBuildLeftIsCleared() {
        assertThat(BuildahCommands.clearing()).containsExactly(
                List.of("docker", "exec", "inventory-compare", "buildah", "rm", "--all"),
                List.of("docker", "exec", "inventory-compare", "buildah", "rmi", "--prune"));
    }

    @Test
    void removingAnImageForcesItsRemovalByTag() {
        assertThat(BuildahCommands.remove("compare/x:original")).containsExactly(
                "docker", "exec", "inventory-compare", "buildah", "rmi", "-f", "compare/x:original");
    }

    @Test
    void anImageIsInspectedAsAnImage() {
        assertThat(BuildahCommands.inspect("compare/x:original")).containsExactly(
                "docker", "exec", "inventory-compare", "buildah", "inspect", "--type", "image", "compare/x:original");
    }

    @Test
    void anImagesFilesAreReadFromAMountedWorkingContainerAsATarWithNumericOwners() {
        assertAll(
                () -> assertThat(BuildahCommands.from("compare/x:original")).containsExactly(
                        "docker", "exec", "inventory-compare", "buildah", "from", "-q", "compare/x:original"),
                () -> assertThat(BuildahCommands.mount("c1")).containsExactly(
                        "docker", "exec", "inventory-compare", "buildah", "mount", "c1"),
                () -> assertThat(BuildahCommands.tar("/m")).containsExactly(
                        "docker", "exec", "inventory-compare", "tar", "-C", "/m", "--numeric-owner", "-cf", "-", "."),
                () -> assertThat(BuildahCommands.removeContainer("c1")).containsExactly(
                        "docker", "exec", "inventory-compare", "buildah", "rm", "c1"));
    }

    @Test
    void theWorkFolderAndArchitectureAreKept() {
        assertAll(
                () -> assertThat(commands.work()).isEqualTo(Path.of("/tmp/compare/work")),
                () -> assertThat(commands.arch()).isEqualTo("arm64"));
    }
}
