package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

class StageScanTest {

    private static StageScan scanned(String dockerfile, StageEnd inherited, Map<String, String> globalArgs) {
        StageScan scan = new StageScan(inherited, globalArgs);
        Parsed.instructions(dockerfile).forEach(scan::accept);
        return scan;
    }

    @Test
    void startsFromWhatTheStageInheritsAndEndsWithItsOwnUserPortsAndEntrypoint() {
        StageScan scan = scanned("FROM base\nEXPOSE 8443/tcp 53/udp\nUSER $UID\nENTRYPOINT run\n",
                new StageEnd("0", List.of("9090"), EntrypointForm.EXEC), Map.of());

        assertThat(scan.result()).isEqualTo(new StageEnd("${UID}", List.of("53/udp", "8443", "9090"),
                EntrypointForm.SHELL));
    }

    @Test
    void envAndArgSetVariablesAndAnArgWithoutDefaultTakesTheGlobalOne() {
        StageScan scan = scanned("FROM ubi9\nARG UID GID=0\nENV HOME=/home/app\n", StageEnd.NONE,
                Map.of("UID", "1001"));

        assertThat(scan.variables()).containsExactlyInAnyOrderEntriesOf(
                Map.of("UID", "1001", "GID", "0", "HOME", "/home/app"));
    }

    @Test
    void anArgWithoutDefaultOrGlobalSetsNothing() {
        StageScan scan = new StageScan(StageEnd.NONE, Map.of());

        scan.arg(Parsed.first("FROM ubi9\nARG NOPE\n", Docker.Arg.class));

        assertThat(scan.variables()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "ENTRYPOINT [\"/run.sh\"] | EXEC",
        "ENTRYPOINT /run.sh       | SHELL",
    })
    void formIsExecForAJsonArrayAndShellOtherwise(String entrypoint, EntrypointForm form) {
        assertThat(StageScan.form(Parsed.first("FROM ubi9\n" + entrypoint + "\n", Docker.Entrypoint.class)))
                .isEqualTo(form);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "8080     | 8080",
        "53/udp   | 53",
        "${PORT}  | 2147483647",
    })
    void portNumberIsTheLeadingDigitsAndAVariableSortsLast(String port, int number) {
        assertThat(StageScan.portNumber(port)).isEqualTo(number);
    }

    @Test
    void portOrderSortsByNumberThenText() {
        assertThat(Stream.of("${PORT}", "8080", "53/udp", "53/tcp").sorted(StageScan.PORT_ORDER).toList())
                .containsExactly("53/tcp", "53/udp", "8080", "${PORT}");
    }
}
