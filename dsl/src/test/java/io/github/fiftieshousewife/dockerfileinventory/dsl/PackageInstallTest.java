package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PackageInstallTest {

    @Test
    void packagesAreCopiedSoTheInstallCannotChangeAfterItIsBuilt() {
        List<String> packages = new ArrayList<>(List.of("git"));
        PackageInstall install = new PackageInstall(PackageManager.MICRODNF, packages);
        packages.add("tar");

        assertThat(install).satisfies(
                i -> assertThat(i.packages()).containsExactly("git"),
                i -> assertThat(i.flags()).isEmpty());
    }

    @Test
    void anInstallNeedsAtLeastOneNamedPackage() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PackageInstall(PackageManager.DNF, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PackageInstall(PackageManager.DNF, List.of(" ")));
    }
}
