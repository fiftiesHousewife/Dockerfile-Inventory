package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageInstall;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageManager;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import java.util.List;
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
    void aRunWithFlagsOrInExecFormStaysRawAsPrinted() {
        assertThat(ConvertCommands.run(run("RUN --network=none make"), "printed"))
                .isEqualTo(new Instruction.Raw("printed", "RUN with flags"));
        assertThat(ConvertCommands.run(run("RUN [\"make\"]"), "printed"))
                .isEqualTo(new Instruction.Raw("printed", "RUN in exec or heredoc form"));
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
