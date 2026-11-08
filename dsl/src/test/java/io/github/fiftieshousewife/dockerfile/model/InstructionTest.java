package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InstructionTest {

    @Test
    void invalidValuesAreRefusedWhenTheInstructionIsBuilt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Expose(List.of()))
                .withMessageContaining("port");
        assertThatIllegalArgumentException().isThrownBy(() -> new User(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Workdir(""));
        assertThatIllegalArgumentException().isThrownBy(() -> new Arg(" ", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Copy(List.of("a"), "", null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Raw("RUN x", " "));
        assertThat(new Expose(new ArrayList<>(List.of(Port.of(80)))).ports()).isUnmodifiable();
    }

    @Test
    void shellVolumeStopSignalAndMaintainerRefuseEmptyValuesAndCopyTheirLists() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Shell(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Volume(List.of(), true));
        assertThatIllegalArgumentException().isThrownBy(() -> new StopSignal(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Maintainer(""));
        assertThat(new Shell(new ArrayList<>(List.of("/bin/bash", "-c"))).command()).isUnmodifiable();
        assertThat(new Volume(new ArrayList<>(List.of("/data")), false).paths()).isUnmodifiable();
    }

    @Test
    void aCopyHeredocNeedsADestinationAndADelimiterAndCopiesItsLines() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CopyHeredoc(" ", "EOF", true, List.of("x")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CopyHeredoc("/etc/x", "", true, List.of("x")));
        assertThat(new CopyHeredoc("/etc/x", "EOF", true, new ArrayList<>(List.of("x"))).lines())
                .isUnmodifiable();
    }

    @Test
    void anAddNeedsASourceAndADestinationAndCopiesItsSources() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Add(List.of(), "/opt/", null, null, null,
                false, false, false));
        assertThatIllegalArgumentException().isThrownBy(() -> new Add(List.of("a.tar"), " ", null, null,
                null, false, false, false));
        assertThat(new Add(new ArrayList<>(List.of("a.tar")), "/opt/", null, null, null, false, false,
                false).sources()).isUnmodifiable();
    }

    @Test
    void aHealthcheckTakesDockersOptionsAShellFormOfOneCommandAndNoOptionsWithNone() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Healthcheck(Map.of("every", "5s"),
                List.of("true"), CommandForm.EXEC)).withMessageContaining("every");
        assertThatIllegalArgumentException().isThrownBy(() -> new Healthcheck(Map.of(),
                List.of("curl", "-f"), CommandForm.SHELL));
        assertThatIllegalArgumentException().isThrownBy(() -> new Healthcheck(Map.of("retries", "3"),
                List.of(), CommandForm.EXEC));
        assertThat(Healthcheck.NONE.command()).isEmpty();
    }

    @Test
    void anOnbuildRefusesAnOnbuildOrAMaintainerInside() {
        Instruction user = new User("1001");

        assertThatIllegalArgumentException().isThrownBy(() -> new Onbuild(new Onbuild(user)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Onbuild(new Maintainer("payments")));
        assertThat(new Onbuild(user).instruction()).isEqualTo(user);
    }

    @Test
    void aShellFormCommandIsOneStringAndACommandWrittenWithoutAFormIsExec() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Cmd(List.of("node", "server.js"), CommandForm.SHELL));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Entrypoint(List.of(), CommandForm.SHELL));
        assertThat(List.of(new Cmd(List.of("serve")).form(),
                new Entrypoint(List.of("app")).form())).containsOnly(CommandForm.EXEC);
    }

    @Test
    void aCopyIsItsSourcesThenItsDestinationWithNothingElseSet() {
        assertThat(Copy.of("a", "b").to("/dst/"))
                .isEqualTo(new Copy(List.of("a", "b"), "/dst/", null, null));
    }

    @Test
    void eachWithMethodSetsOnlyItsPart() {
        Copy copy = Copy.of("run.sh").to("/bin/");

        assertAll(
                () -> assertThat(copy.from("build").fromStage()).isEqualTo("build"),
                () -> assertThat(copy.ownedBy("1001:0").chown()).isEqualTo("1001:0"),
                () -> assertThat(copy.mode("755").chmod()).isEqualTo("755"),
                () -> assertThat(copy.linked().link()).isTrue(),
                () -> assertThat(copy.inJsonForm().jsonForm()).isTrue(),
                () -> assertThat(copy.from("build").mode("755"))
                        .isEqualTo(
                                new Copy(List.of("run.sh"), "/bin/", null, "build", "755", false, false)));
    }

    @Test
    void anAddIsItsSourcesThenItsDestinationWithNothingElseSet() {
        assertThat(Add.of("a.tgz").to("/opt/"))
                .isEqualTo(new Add(List.of("a.tgz"), "/opt/", null, null, null, false, false, false));
    }

    @Test
    void eachAddWithMethodSetsOnlyItsPart() {
        Add add = Add.of("https://github.com/o/r.git").to("/src");

        assertAll(
                () -> assertThat(add.ownedBy("1001:0").chown()).isEqualTo("1001:0"),
                () -> assertThat(add.mode("644").chmod()).isEqualTo("644"),
                () -> assertThat(add.checksum("sha256:ab").checksum()).isEqualTo("sha256:ab"),
                () -> assertThat(add.keepingGitDir().keepGitDir()).isTrue(),
                () -> assertThat(add.linked().link()).isTrue(),
                () -> assertThat(add.inJsonForm().jsonForm()).isTrue(),
                () -> assertThat(add.mode("644").linked()).isEqualTo(new Add(
                        List.of("https://github.com/o/r.git"), "/src", null, "644", null, false, true, false)));
    }

    @Test
    void aCopyHeredocIsItsDestinationThenItsLinesQuotedUnderEofUnlessSaidOtherwise() {
        CopyHeredoc heredoc = CopyHeredoc.to("/etc/a.conf").lines("a = 1", "b = 2");

        assertAll(
                () -> assertThat(heredoc)
                        .isEqualTo(new CopyHeredoc("/etc/a.conf", "EOF", true, List.of("a = 1", "b = 2"))),
                () -> assertThat(heredoc.delimitedBy("END").delimiter()).isEqualTo("END"),
                () -> assertThat(heredoc.expanding().quoted()).isFalse());
    }

    @Test
    void aCopyWrittenWithoutOptionsHasNone() {
        assertThat(new Copy(List.of("a"), "/b/", null, null))
                .isEqualTo(new Copy(List.of("a"), "/b/", null, null, null, false, false));
    }

    @Test
    void listsAreCopiedSoTheInstructionCannotChangeAfterItIsBuilt() {
        assertThat(new Entrypoint(new ArrayList<>(List.of("java"))).command()).isUnmodifiable();
        assertThat(new Cmd(new ArrayList<>(List.of("serve"))).command()).isUnmodifiable();
        assertThat(new Copy(new ArrayList<>(List.of("app.jar")), "/opt/app/", "1001:0", null).sources())
                .isUnmodifiable();
    }

    @Test
    void mapsAreCopiedInOrderSoTheInstructionCannotChangeAfterItIsBuilt() {
        Map<String, String> values = new LinkedHashMap<>(Map.of("A", "1"));
        Env env = new Env(values);
        Label label = new Label(values);
        values.put("B", "2");

        assertThat(env.values()).containsOnlyKeys("A").isUnmodifiable();
        assertThat(label.values()).containsOnlyKeys("A").isUnmodifiable();
    }

    @Test
    void aRunHoldsItsBody() {
        assertThat(new Run(new RunBody.Shell(List.of("make"))).body())
                .isEqualTo(new RunBody.Shell(List.of("make")));
    }
}
