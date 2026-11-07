package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.CommandForm;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.OrderedMap;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageInstall;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageManager;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Port;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunFlag;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuilderCallsTest {

    @Test
    void callWritesEachInstructionAsItsBuilderMethod() {
        assertThat(List.of(new Instruction.Arg("A", null), new Instruction.Arg("A", "1"),
                new Instruction.Env(Map.of("K", "v")), new Instruction.Expose(List.of(Port.of(8080))),
                new Instruction.Raw("VOLUME /d", "VOLUME is not modelled")))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".arg(\"A\")", ".arg(\"A\", \"1\")",
                        ".env(io.github.fiftieshousewife.dockerfileinventory.dsl.OrderedMap"
                                + ".of(\"K\", \"v\"))", ".expose(\"8080\")",
                        ".raw(\"VOLUME /d\", \"VOLUME is not modelled\")");
    }

    @Test
    void aCopyWithOptionsIsWrittenWithThemAndOneWithoutInShort() {
        assertThat(List.of(new Instruction.Copy(List.of("a"), "/b/", null, "build"),
                new Instruction.Copy(List.of("a"), "/b/", "1001", null, "755", true, false)))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".copy(java.util.List.of(\"a\"), \"/b/\", null, \"build\")",
                        ".copy(java.util.List.of(\"a\"), \"/b/\", \"1001\", null, \"755\", true, false)");
    }

    @Test
    void aCopyHeredocIsWrittenAsWritten() {
        assertThat(BuilderCalls.call(new Instruction.CopyHeredoc("/etc/a", "EOF", true, List.of("x = 1"))).toString())
                .isEqualTo(".copyHeredoc(\"/etc/a\", \"EOF\", true, java.util.List.of(\"x = 1\"))");
    }

    @Test
    void anAddIsWrittenAsWritten() {
        assertThat(BuilderCalls.call(new Instruction.Add(List.of("a.tgz"), "/opt/", null, "644", null, false, true,
                false)).toString())
                .isEqualTo(".add(java.util.List.of(\"a.tgz\"), \"/opt/\", null, \"644\", null, false, true, false)");
    }

    @Test
    void aRunsFlagsAreWrittenBeforeItsCommandsOrInstall() {
        List<RunFlag> flags = List.of(RunFlag.Mount.parse("type=cache,target=/c"), new RunFlag.Network("none"));
        String written = "java.util.List.of(io.github.fiftieshousewife.dockerfileinventory.dsl.RunFlag.Mount.parse("
                + "\"type=cache,target=/c\"), new io.github.fiftieshousewife.dockerfileinventory.dsl.RunFlag.Network("
                + "\"none\"))";

        assertThat(List.of(new Instruction.Run(new RunBody.Shell(List.of("make")), flags),
                new Instruction.Run(new RunBody.Install(new PackageInstall(PackageManager.APK, List.of(),
                        List.of("git"))), flags)))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".run(" + written + ", \"make\")",
                        ".install(" + written + ", io.github.fiftieshousewife.dockerfileinventory.dsl.PackageManager"
                                + ".APK, java.util.List.of(), java.util.List.of(\"git\"))");
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
    void aMapIsWrittenInItsOrder() {
        assertThat(BuilderCalls.map(OrderedMap.of("B", "2", "A", "1")).toString())
                .isEqualTo("io.github.fiftieshousewife.dockerfileinventory.dsl.OrderedMap"
                        + ".of(\"B\", \"2\", \"A\", \"1\")");
    }

    @Test
    void anExecOrHeredocRunIsWrittenWithItsFlags() {
        List<RunFlag> none = List.of();
        assertThat(List.of(new Instruction.Run(new RunBody.Exec(List.of("make", "all")), none),
                new Instruction.Run(new RunBody.Heredoc("", "EOF", true, List.of("make")), none)))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".runExec(java.util.List.of(), \"make\", \"all\")",
                        ".runHeredoc(java.util.List.of(), \"\", \"EOF\", true, java.util.List.of(\"make\"))");
    }

    @Test
    void aHealthcheckIsWrittenAsWrittenAndAnOnbuildAsItsInstructionThenAsOnbuild() {
        assertThat(List.of(Instruction.Healthcheck.NONE,
                new Instruction.Healthcheck(Map.of("retries", "3"), List.of("true"), CommandForm.SHELL),
                new Instruction.Onbuild(new Instruction.User("1001"))))
                .map(instruction -> BuilderCalls.call(instruction).toString())
                .containsExactly(".healthcheckNone()",
                        ".healthcheck(io.github.fiftieshousewife.dockerfileinventory.dsl.OrderedMap"
                                + ".of(\"retries\", \"3\"), java.util.List.of(\"true\"), "
                                + "io.github.fiftieshousewife.dockerfileinventory.dsl.CommandForm.SHELL)",
                        ".user(\"1001\").asOnbuild()");
    }
}
