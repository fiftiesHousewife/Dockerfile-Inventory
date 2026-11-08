package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.model.PackageInstall;
import io.github.fiftieshousewife.dockerfile.model.PackageManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallsTest {

    @Test
    void ofRecognisesEachManagersInstallAndItsClean() {
        assertThat(List.of("yum install -y git && yum clean all", "apk add --no-cache git",
                "apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*"))
                .map(command -> Installs.of(command).orElseThrow().manager())
                .containsExactly(PackageManager.YUM, PackageManager.APK, PackageManager.APT);
    }

    @Test
    void ofTurnsDownVariablesRedirectionsLeadingAssignmentsAndOtherCommands() {
        assertThat(List.of("apk add --no-cache $PKGS", "apk add --no-cache git > /log", "make && make install",
                "A=1 apk add --no-cache git", "dnf install -y git"))
                .allSatisfy(command -> assertThat(Installs.of(command)).isEmpty());
    }

    @Test
    void rpmNeedsTheSameToolsCleanAll() {
        assertThat(Installs.rpm(List.of(List.of("dnf", "install", "git"), List.of("dnf", "clean", "all"))))
                .contains(new PackageInstall(PackageManager.DNF, List.of(), List.of("git")));
        assertThat(Installs.rpm(List.of(List.of("dnf", "install", "git"), List.of("yum", "clean", "all")))).isEmpty();
        assertThat(Installs.rpm(List.of(List.of("dnf", "install", "git")))).isEmpty();
    }

    @Test
    void apkNeedsNoCache() {
        assertThat(Installs.apk(List.of(List.of("apk", "add", "--no-cache", "git"))))
                .contains(new PackageInstall(PackageManager.APK, List.of("--no-cache"), List.of("git")));
        assertThat(Installs.apk(List.of(List.of("apk", "add", "git")))).isEmpty();
    }

    @Test
    void aptNeedsTheUpdateBeforeAndTheListsRemovedAfter() {
        assertThat(Installs.apt(List.of(List.of("apt-get", "update"), List.of("apt-get", "install", "curl"),
                List.of("rm", "-rf", "/var/lib/apt/lists/*"))))
                .contains(new PackageInstall(PackageManager.APT, List.of(), List.of("curl")));
        assertThat(Installs.apt(List.of(List.of("apt-get", "update"), List.of("apt-get", "install", "curl"),
                List.of("rm", "-rf", "/tmp")))).isEmpty();
    }

    @Test
    void installSplitsFlagsFromPackagesAndTurnsDownAFlagWithAnArgumentOrNoPackages() {
        assertThat(Installs.install(PackageManager.DNF, List.of("-y", "git", "--nodocs", "tar")))
                .contains(new PackageInstall(PackageManager.DNF, List.of("-y", "--nodocs"), List.of("git", "tar")));
        assertThat(Installs.install(PackageManager.DNF, List.of("--repo", "x", "git"))).isEmpty();
        assertThat(Installs.install(PackageManager.DNF, List.of("-y"))).isEmpty();
    }

    @Test
    void eachCommandMustStartWithItsOwnFirstWord() {
        List<List<String>> commands = List.of(List.of("apt-get", "update"), List.of("apt-get", "install", "x"));

        assertThat(Installs.eachCommandIsItsOwnWords("apt-get update && apt-get install x", commands)).isTrue();
        assertThat(Installs.eachCommandIsItsOwnWords("A=1 apt-get update && apt-get install x", commands)).isFalse();
        assertThat(Installs.eachCommandIsItsOwnWords("apt-get update", commands)).isFalse();
    }
}
