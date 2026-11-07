package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

class RunScriptsTest {

    private static Docker.Run run(String run) {
        return Parsed.first("FROM ubi9\n" + run + "\n", Docker.Run.class);
    }

    @Test
    void aShellFormRunsScriptIsItsText() {
        assertThat(RunScripts.script(run("RUN make && make install"))).contains("make && make install");
    }

    @Test
    void aHeredocRunsScriptIsItsBodyLines() {
        assertThat(RunScripts.script(run("RUN <<EOF\nmake\nmake install\nEOF"))).contains("make\n\nmake install\n");
    }

    @Test
    void anExecFormRunHasNoScriptAndItsWordsAreOneCommand() {
        Docker.Run exec = run("RUN [\"dnf\", \"install\", \"git\"]");

        assertThat(RunScripts.script(exec)).isEmpty();
        assertThat(RunScripts.commands(exec)).contains(List.of(List.of("dnf", "install", "git")));
    }

    @Test
    void aShellFormRunsCommandsAreTokenised() {
        assertThat(RunScripts.commands(run("RUN make && make install")))
                .contains(List.of(List.of("make"), List.of("make", "install")));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "SHELL [\"powershell\", \"-Command\"]                | true",
        "SHELL [\"C:\\\\Windows\\\\cmd.exe\", \"/S\", \"/C\"] | true",
        "SHELL [\"/usr/bin/pwsh\", \"-c\"]                   | true",
        "SHELL [\"/bin/bash\", \"-c\"]                       | false",
    })
    void otherShellIsPowershellPwshOrCmdWhateverItsPath(String shell, boolean other) {
        assertThat(RunScripts.otherShell(Parsed.first("FROM ubi9\n" + shell + "\n", Docker.Shell.class)))
                .isEqualTo(other);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "'\"PowerShell.exe\"' | true",
        "C:\\Windows\\cmd     | true",
        "/bin/sh             | false",
    })
    void isOtherShellIgnoresQuotesCaseAPathAndAnExeSuffix(String program, boolean other) {
        assertThat(RunScripts.isOtherShell(program)).isEqualTo(other);
    }

    @Test
    void aShellFormRunsScriptDropsCommentLinesAsDockerDoesEvenInsideAQuotedString() {
        Docker.Run quoted = run("RUN conf=' \\\n    A=y \\\n# we've removed B \\\n    C=y'; \\\n    echo \"$conf\"");

        assertThat(RunScripts.script(quoted)).hasValueSatisfying(script -> assertThat(script).doesNotContain("we've"));
        assertThat(RunScripts.commands(quoted)).isPresent();
    }
}
