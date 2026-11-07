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
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Expose(List.of()))
                .withMessageContaining("port");
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.User(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Workdir(""));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Arg(" ", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Copy(List.of("a"), "", null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Raw("RUN x", " "));
        assertThat(new Instruction.Expose(new ArrayList<>(List.of(Port.of(80)))).ports()).isUnmodifiable();
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
    void aCopyHeredocNeedsADestinationAndADelimiterAndCopiesItsLines() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Instruction.CopyHeredoc(" ", "EOF", true, List.of("x")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Instruction.CopyHeredoc("/etc/x", "", true, List.of("x")));
        assertThat(new Instruction.CopyHeredoc("/etc/x", "EOF", true, new ArrayList<>(List.of("x"))).lines())
                .isUnmodifiable();
    }

    @Test
    void anAddNeedsASourceAndADestinationAndCopiesItsSources() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Add(List.of(), "/opt/", null, null, null,
                false, false, false));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Add(List.of("a.tar"), " ", null, null,
                null, false, false, false));
        assertThat(new Instruction.Add(new ArrayList<>(List.of("a.tar")), "/opt/", null, null, null, false, false,
                false).sources()).isUnmodifiable();
    }

    @Test
    void aHealthcheckTakesDockersOptionsAShellFormOfOneCommandAndNoOptionsWithNone() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Healthcheck(Map.of("every", "5s"),
                List.of("true"), CommandForm.EXEC)).withMessageContaining("every");
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Healthcheck(Map.of(),
                List.of("curl", "-f"), CommandForm.SHELL));
        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Healthcheck(Map.of("retries", "3"),
                List.of(), CommandForm.EXEC));
        assertThat(Instruction.Healthcheck.NONE.command()).isEmpty();
    }

    @Test
    void anOnbuildRefusesAnOnbuildOrAMaintainerInside() {
        Instruction user = new Instruction.User("1001");

        assertThatIllegalArgumentException().isThrownBy(() -> new Instruction.Onbuild(new Instruction.Onbuild(user)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Instruction.Onbuild(new Instruction.Maintainer("payments")));
        assertThat(new Instruction.Onbuild(user).instruction()).isEqualTo(user);
    }

    @Test
    void aCopyWrittenWithoutOptionsHasNone() {
        assertThat(new Instruction.Copy(List.of("a"), "/b/", null, null))
                .isEqualTo(new Instruction.Copy(List.of("a"), "/b/", null, null, null, false, false));
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
