package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InstructionTest {

    @Test
    void invalidValuesAreRefusedWhenTheInstructionIsBuilt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Expose(0)).withMessageContaining("port");
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Expose(65536));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.User(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Workdir(""));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Arg(" ", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Copy(List.of("a"), "", null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Raw("RUN x", " "));
        assertThat(new Instruction.Expose(65535).port()).isEqualTo(65535);
    }

    @Test
    void shellVolumeStopSignalAndMaintainerRefuseEmptyValuesAndCopyTheirLists() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Shell(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Volume(List.of(), true));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.StopSignal(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Maintainer(""));
        assertThat(new Instruction.Shell(new ArrayList<>(List.of("/bin/bash", "-c"))).command()).isUnmodifiable();
        assertThat(new Instruction.Volume(new ArrayList<>(List.of("/data")), false).paths()).isUnmodifiable();
    }

    @Test
    void listsAreCopiedSoTheInstructionCannotChangeAfterItIsBuilt() {
        assertThat(new Instruction.Entrypoint(new ArrayList<>(List.of("java"))).command()).isUnmodifiable();
        assertThat(new Instruction.Cmd(new ArrayList<>(List.of("serve"))).command()).isUnmodifiable();
        assertThat(new Instruction.Copy(new ArrayList<>(List.of("app.jar")), "/opt/app/", "1001:0", null).sources())
                .isUnmodifiable();
    }

    @Test
    void mapsAreCopiedInOrderSoTheInstructionCannotChangeAfterItIsBuilt() {
        Map<String, String> values = new LinkedHashMap<>(Map.of("A", "1"));
        Instruction.Env env = new Instruction.Env(values);
        Instruction.Label label = new Instruction.Label(values);
        values.put("B", "2");

        assertThat(env.values()).containsOnlyKeys("A").isUnmodifiable();
        assertThat(label.values()).containsOnlyKeys("A").isUnmodifiable();
    }

    @Test
    void aRunHoldsItsBody() {
        assertThat(new Instruction.Run(new RunBody.Shell(List.of("make"))).body())
                .isEqualTo(new RunBody.Shell(List.of("make")));
    }
}
