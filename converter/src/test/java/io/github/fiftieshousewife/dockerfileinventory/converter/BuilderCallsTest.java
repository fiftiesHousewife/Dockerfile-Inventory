package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.model.CommandForm;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import io.github.fiftieshousewife.dockerfile.model.OrderedMap;
import io.github.fiftieshousewife.dockerfile.model.PackageInstall;
import io.github.fiftieshousewife.dockerfile.model.PackageManager;
import io.github.fiftieshousewife.dockerfile.model.Port;
import io.github.fiftieshousewife.dockerfile.model.RunBody;
import io.github.fiftieshousewife.dockerfile.model.RunFlag;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuilderCallsTest {

    private static final String DSL = "io.github.fiftieshousewife.dockerfile.model.";

    @Test
    void callWritesEachInstructionAsItsBuilderMethod() {
        assertThat(List.of(new Instruction.Arg("A", null), new Instruction.Arg("A", "1"),
                new Instruction.Env(Map.of("K", "v")), new Instruction.Expose(List.of(Port.of(8080))),
                new Instruction.Raw("VOLUME /d", "VOLUME is not modelled")))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".arg(\"A\")", ".arg(\"A\", \"1\")",
                        ".env(io.github.fiftieshousewife.dockerfile.model.OrderedMap"
                                + ".of(\"K\", \"v\"))", ".expose(\"8080\")",
                        ".raw(\"VOLUME /d\", \"VOLUME is not modelled\")");
    }

    @Test
    void aCopyWithOnlyASourceAndDestinationIsShortAndAnyOtherIsItsRecordWithWhatIsSet() {
        assertThat(List.of(new Instruction.Copy(List.of("a"), "/b/", null, null),
                new Instruction.Copy(List.of("a", "c"), "/b/", "1001", "build", "755", true, true)))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".copy(\"a\", \"/b/\")",
                        ".copy(" + DSL + "Instruction.Copy.of(\"a\", \"c\").to(\"/b/\").from(\"build\")"
                                + ".ownedBy(\"1001\").mode(\"755\").linked().inJsonForm())");
    }

    @Test
    void aCopyWithNoSourcesIsStillItsRecord() {
        assertThat(BuilderCalls.copy(new Instruction.Copy(List.of(), "/d/", null, null)).toString())
                .isEqualTo(".copy(" + DSL + "Instruction.Copy.of().to(\"/d/\"))");
    }

    @Test
    void anAddIsItsRecordWithWhatIsSet() {
        assertThat(List.of(new Instruction.Add(List.of("a.tgz"), "/opt/", null, null, null, false, false, false),
                new Instruction.Add(List.of("r.git"), "/s", "1", "644", "sha256:ab", true, true, true)))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".add(" + DSL + "Instruction.Add.of(\"a.tgz\").to(\"/opt/\"))",
                        ".add(" + DSL + "Instruction.Add.of(\"r.git\").to(\"/s\").ownedBy(\"1\").mode(\"644\")"
                                + ".checksum(\"sha256:ab\").keepingGitDir().linked().inJsonForm())");
    }

    @Test
    void aCopyHeredocIsItsRecordWithWhatDiffersFromTheDefaults() {
        assertThat(List.of(new Instruction.CopyHeredoc("/etc/a", "EOF", true, List.of("x = 1")),
                new Instruction.CopyHeredoc("/etc/b", "END", false, List.of("$X"))))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".copyHeredoc(" + DSL + "Instruction.CopyHeredoc.to(\"/etc/a\").lines(\"x = 1\"))",
                        ".copyHeredoc(" + DSL + "Instruction.CopyHeredoc.to(\"/etc/b\").lines(\"$X\")"
                                + ".delimitedBy(\"END\").expanding())");
    }

    @Test
    void aRunIsItsShortMethodWithoutFlagsAndItsFlagsAndBodyWithThem() {
        List<RunFlag> flags = List.of(new RunFlag.Network("none"));
        String none = "java.util.List.of(new " + DSL + "RunFlag.Network(\"none\"))";
        RunBody install = new RunBody.Install(new PackageInstall(PackageManager.APK, List.of(), List.of("git")));

        assertThat(List.of(new Instruction.Run(new RunBody.Shell(List.of("make")), flags),
                new Instruction.Run(install, flags),
                new Instruction.Run(new RunBody.Exec(List.of("make", "all"))),
                new Instruction.Run(new RunBody.Exec(List.of("make")), flags),
                new Instruction.Run(new RunBody.Heredoc("bash", "EOF", false, List.of("make")))))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".run(" + none + ", \"make\")",
                        ".run(" + none + ", new " + DSL + "RunBody.Install(new " + DSL + "PackageInstall(" + DSL
                                + "PackageManager.APK, java.util.List.of(), java.util.List.of(\"git\"))))",
                        ".runExec(\"make\", \"all\")",
                        ".run(" + none + ", new " + DSL + "RunBody.Exec(java.util.List.of(\"make\")))",
                        ".run(java.util.List.of(), " + DSL + "RunBody.Heredoc.of(\"make\").command(\"bash\")"
                                + ".expanding())");
    }

    @Test
    void severalPortsAreWrittenAsTheirSpecs() {
        assertThat(BuilderCalls.call(new Instruction.Expose(List.of(Port.of(80), new Port("53/udp")))).toString())
                .isEqualTo(".expose(\"80\", \"53/udp\")");
    }

    @Test
    void shellVolumeStopSignalAndMaintainerAreWrittenAsTheirBuilderMethods() {
        assertThat(List.of(new Instruction.Shell(List.of("/bin/bash", "-c")),
                new Instruction.Volume(List.of("/data"), false), new Instruction.StopSignal("SIGTERM"),
                new Instruction.Maintainer("payments")))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".shell(\"/bin/bash\", \"-c\")", ".volume(java.util.List.of(\"/data\"), false)",
                        ".stopSignal(\"SIGTERM\")", ".maintainer(\"payments\")");
    }

    @Test
    void aShellFormCmdOrEntrypointIsWrittenWithItsForm() {
        assertThat(List.of(new Instruction.Entrypoint(List.of("exec app"), CommandForm.SHELL),
                new Instruction.Cmd(List.of("serve"), CommandForm.SHELL), new Instruction.Cmd(List.of("serve"))))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(
                        ".entrypoint(java.util.List.of(\"exec app\"), "
                                + "io.github.fiftieshousewife.dockerfile.model.CommandForm.SHELL)",
                        ".cmd(java.util.List.of(\"serve\"), "
                                + "io.github.fiftieshousewife.dockerfile.model.CommandForm.SHELL)",
                        ".cmd(\"serve\")");
    }

    @Test
    void aMapIsWrittenInItsOrder() {
        assertThat(BuilderCalls.map(OrderedMap.of("B", "2", "A", "1")).toString())
                .isEqualTo("io.github.fiftieshousewife.dockerfile.model.OrderedMap"
                        + ".of(\"B\", \"2\", \"A\", \"1\")");
    }

    @Test
    void aHealthcheckIsWrittenAsWrittenAndAnOnbuildAsItsInstructionThenAsOnbuild() {
        assertThat(List.of(Instruction.Healthcheck.NONE,
                new Instruction.Healthcheck(Map.of("retries", "3"), List.of("true"), CommandForm.SHELL),
                new Instruction.Onbuild(new Instruction.User("1001"))))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".healthcheckNone()",
                        ".healthcheck(io.github.fiftieshousewife.dockerfile.model.OrderedMap"
                                + ".of(\"retries\", \"3\"), java.util.List.of(\"true\"), "
                                + "io.github.fiftieshousewife.dockerfile.model.CommandForm.SHELL)",
                        ".user(\"1001\").asOnbuild()");
    }
}
