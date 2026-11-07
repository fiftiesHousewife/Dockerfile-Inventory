package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class PackageInstallRendererTest {

    private static final List<String> PACKAGES = List.of("git", "tar");

    @Test
    void eachManagerInstallsAndCleansInTheSameRun() {
        assertThat(commands(PackageManager.MICRODNF)).containsExactly(
                "microdnf install -y --nodocs --setopt=install_weak_deps=0 git tar", "microdnf clean all");
        assertThat(commands(PackageManager.DNF)).containsExactly(
                "dnf install -y --nodocs --setopt=install_weak_deps=0 git tar", "dnf clean all");
        assertThat(commands(PackageManager.YUM)).containsExactly(
                "yum install -y --nodocs --setopt=install_weak_deps=0 git tar", "yum clean all");
        assertThat(commands(PackageManager.APK)).containsExactly("apk add --no-cache git tar");
        assertThat(commands(PackageManager.APT)).containsExactly("apt-get update",
                "apt-get install -y --no-install-recommends git tar", "rm -rf /var/lib/apt/lists/*");
    }

    @Test
    void theManagerFollowsTheBasesFamilyAndAnUnknownBaseMustNameOne() {
        assertThat(PackageInstallRenderer.forBase(base("registry.example.com/ubi9/ubi-minimal")))
                .isEqualTo(PackageManager.MICRODNF);
        assertThat(PackageInstallRenderer.forBase(base("ubi9/ubi"))).isEqualTo(PackageManager.DNF);
        assertThat(PackageInstallRenderer.forBase(base("alpine"))).isEqualTo(PackageManager.APK);
        assertThat(PackageInstallRenderer.forBase(base("debian"))).isEqualTo(PackageManager.APT);
        assertThat(PackageInstallRenderer.forBase(base("ubuntu"))).isEqualTo(PackageManager.APT);
        assertThatIllegalArgumentException().isThrownBy(() -> PackageInstallRenderer.forBase(base("team/custom")))
                .withMessageContaining("team/custom");
    }

    @Test
    void anInstallWithItsOwnFlagsRendersExactlyThoseFlagsThenTheClean() {
        assertThat(PackageInstallRenderer.commands(
                new PackageInstall(PackageManager.MICRODNF, List.of("-y"), List.of("git", "tar"))))
                .containsExactly("microdnf install -y git tar", "microdnf clean all");
        assertThat(PackageInstallRenderer.commands(
                new PackageInstall(PackageManager.APK, List.of("--no-cache", "--virtual", ".build"), List.of("gcc"))))
                .containsExactly("apk add --no-cache --virtual .build gcc");
        assertThat(PackageInstallRenderer.commands(
                new PackageInstall(PackageManager.APT, List.of("-y"), List.of("curl"))))
                .containsExactly("apt-get update", "apt-get install -y curl", "rm -rf /var/lib/apt/lists/*");
    }

    @Test
    void flagsAreTheInstallsOwnOrElseTheHouseFlagsFollowedByASpace() {
        assertThat(PackageInstallRenderer.flags(new PackageInstall(PackageManager.APK, PACKAGES), "--no-cache"))
                .isEqualTo("--no-cache ");
        assertThat(PackageInstallRenderer.flags(
                new PackageInstall(PackageManager.APK, List.of("-q", "--no-cache"), PACKAGES), "--house"))
                .isEqualTo("-q --no-cache ");
    }

    private static List<String> commands(PackageManager manager) {
        return PackageInstallRenderer.commands(new PackageInstall(manager, PACKAGES));
    }

    private static BaseImage base(String name) {
        return new BaseImage(name, "1", null);
    }
}
