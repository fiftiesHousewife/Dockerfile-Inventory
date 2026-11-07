package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageInstall;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageManager;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunFlag;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import tools.jackson.core.JacksonException;

class ConvertCommandsTest {

    @Test
    void aRunThatIsOneInstallAndItsCleanBecomesAnInstall() {
        assertThat(ConvertCommands.run(run("RUN apk add --no-cache git"), "printed")).isEqualTo(new Instruction.Run(
                new RunBody.Install(new PackageInstall(PackageManager.APK, List.of("--no-cache"), List.of("git")))));
    }

    @Test
    void anyOtherShellFormRunIsShellTextOnOneLine() {
        assertThat(ConvertCommands.run(run("RUN make \\\n    && make install"), "printed"))
                .isEqualTo(new Instruction.Run(new RunBody.Shell(List.of("make && make install"))));
    }

    @Test
    void aRunWithAFlagTheModelLacksOrInExecFormStaysRawAsPrinted() {
        assertThat(ConvertCommands.run(run("RUN --device=/dev/fuse make"), "printed"))
                .isEqualTo(new Instruction.Raw("printed", "RUN with flag --device"));
        assertThat(ConvertCommands.run(run("RUN [\"make\"]"), "printed"))
                .isEqualTo(new Instruction.Raw("printed", "RUN in exec or heredoc form"));
    }

    @Test
    void aRunKeepsItsFlagsInOrderWhetherItIsAnInstallOrShell() {
        assertThat(List.of(run("RUN --mount=type=cache,target=/root/.m2 --network=none mvn package"),
                run("RUN --mount=type=secret,id=t --security=insecure apk add --no-cache git")))
                .map(run -> ConvertCommands.run(run, "printed"))
                .containsExactly(new Instruction.Run(new RunBody.Shell(List.of("mvn package")),
                                List.of(RunFlag.Mount.parse("type=cache,target=/root/.m2"),
                                        new RunFlag.Network("none"))),
                        new Instruction.Run(new RunBody.Install(new PackageInstall(PackageManager.APK,
                                List.of("--no-cache"), List.of("git"))),
                                List.of(RunFlag.Mount.parse("type=secret,id=t"), new RunFlag.Security("insecure"))));
    }

    @Test
    void flagsAreTheRunsFlagsOrNothingWhenOneIsNotMountNetworkOrSecurity() {
        assertThat(List.of(run("RUN make"), run("RUN --network=host make"), run("RUN --network=host --device=x make")))
                .map(ConvertCommands::flags)
                .containsExactly(Optional.of(List.of()), Optional.of(List.of(new RunFlag.Network("host"))),
                        Optional.empty());
    }

    @Test
    void anExecFormsWordsAreDecodedAsJsonStrings() {
        Docker.Cmd cmd = (Docker.Cmd) first("CMD [\"sh\", \"-c\", \"echo \\\"$@\\\"\"]");

        assertThat(ConvertCommands.arguments((Docker.ExecForm) cmd.getCommand()))
                .contains(List.of("sh", "-c", "echo \"$@\""));
    }

    @Test
    void anExecFormWithAWordThatIsNotValidJsonHasNoArguments() {
        Docker.Cmd cmd = (Docker.Cmd) first("CMD [\"grep\", \"\\d+\"]");

        assertThat(ConvertCommands.arguments((Docker.ExecForm) cmd.getCommand())).isEmpty();
    }

    @Test
    void decodedReadsAQuotedOrBareWordAsAJsonString() {
        assertThat(List.of("\"a\\\"b\"", "bare")).map(ConvertCommands::decoded).containsExactly("a\"b", "bare");
        assertThatThrownBy(() -> ConvertCommands.decoded("\"\\d\"")).isInstanceOf(JacksonException.class);
    }

    private static Docker.Run run(String line) {
        return (Docker.Run) first(line);
    }

    private static Docker.Instruction first(String line) {
        return Parsed.of("FROM ubi9\n" + line + "\n").getStages().getFirst().getInstructions().getFirst();
    }
}
