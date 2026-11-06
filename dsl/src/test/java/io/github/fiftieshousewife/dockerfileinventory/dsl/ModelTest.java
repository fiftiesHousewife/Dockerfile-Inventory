package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void aDigestFromABuildArgumentIsModelledButAPlaceholderIsNot() {
        assertThat(new BaseImage("quay.io/prometheus/busybox", null, "sha256:${BASE_DOCKER_SHA}").digest())
                .isEqualTo("sha256:${BASE_DOCKER_SHA}");
        assertThat(new BaseImage("busybox", null, "sha256:$SHA").digest()).isEqualTo("sha256:$SHA");
        assertThatIllegalArgumentException().isThrownBy(() -> new BaseImage("busybox", null, "sha256..."));
    }

    @Test
    void invalidValuesAreRefusedWhenTheModelIsBuilt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BaseImage("ubi9/ubi-minimal", "9.4", "sha256:abc"))
                .withMessageContaining("digest");
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Expose(0)).withMessageContaining("port");
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Expose(65536));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.User(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Workdir(""));
        assertThat(new BaseImage("ubi9/ubi-minimal", "9.4", "sha256:" + "a".repeat(64)).digest()).hasSize(71);
    }

    @Test
    void listsAreCopiedSoTheModelCannotChangeAfterItIsBuilt() {
        List<String> packages = new ArrayList<>(List.of("git"));
        PackageInstall install = new PackageInstall(PackageManager.MICRODNF, packages);
        packages.add("tar");

        assertThat(install.packages()).containsExactly("git");
        assertThat(new Instruction.Entrypoint(new ArrayList<>(List.of("java"))).command()).isUnmodifiable();
        assertThat(new Instruction.Cmd(new ArrayList<>(List.of("serve"))).command()).isUnmodifiable();
        assertThat(new Instruction.Copy(new ArrayList<>(List.of("app.jar")), "/opt/app/", "1001:0", null).sources())
                .isUnmodifiable();
        Map<String, String> values = new LinkedHashMap<>(Map.of("A", "1"));
        Instruction.Env env = new Instruction.Env(values);
        Instruction.Label label = new Instruction.Label(values);
        values.put("B", "2");
        assertThat(env.values()).containsOnlyKeys("A");
        assertThat(label.values()).containsOnlyKeys("A");
        assertThat(new Instruction.Run(new RunBody.Shell(List.of("make"))).body())
                .isEqualTo(new RunBody.Shell(List.of("make")));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Arg(" ", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Copy(List.of("a"), "", null, null));
    }

    @Test
    void structureThatCannotBeRenderedIsRefused() {
        BaseImage ubi = new BaseImage("ubi9/ubi-minimal", "9.4", null);

        assertThatIllegalArgumentException().isThrownBy(() -> new Stage(null, ubi, "build", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Stage(null, null, null, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Dockerfile(List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PackageInstall(PackageManager.DNF, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RunBody.Shell(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Raw("RUN x", " "));
        assertThat(ubi.family()).isEqualTo("ubi-minimal");
    }
}
