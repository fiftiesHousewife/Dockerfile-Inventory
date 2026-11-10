package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DockerInventoryTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final List<Map<String, String>> ran = new ArrayList<>();

    private final Command facts = new Command("facts", "reads each Dockerfile into facts",
            List.of(Setting.required("DISCOVERY_DIR", "discovery's output folder"),
                    Setting.required("OUT_DIR", "where the facts go")),
            ran::add);

    private final Command failing = new Command("rules", "runs the rules", List.of(), env -> {
        throw new IllegalStateException("EOL_LIST /x does not exist\nmore detail");
    });

    private int run(Map<String, String> env, String... args) {
        return DockerInventory.run(List.of(args), env, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), List.of(facts, failing));
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void aCommandRunsWithTheEnvironmentAndItsStatusIsTheExit() {
        Map<String, String> env = Map.of("DISCOVERY_DIR", "out/a/discovery", "OUT_DIR", "out/a/facts");

        assertAll(
                () -> assertThat(run(env, "facts")).isZero(),
                () -> assertThat(ran).containsExactly(env));
    }

    @Test
    void theHelpListsEveryCommandWithItsSummary() {
        assertAll(
                () -> assertThat(run(Map.of(), "--help")).isZero(),
                () -> assertThat(out()).contains("usage: docker-inventory <command>", "facts", "reads each Dockerfile",
                        "rules", "runs the rules", "--version"));
    }

    @Test
    void noCommandIsAUsageError() {
        assertAll(
                () -> assertThat(run(Map.of())).isEqualTo(2),
                () -> assertThat(err()).contains("usage: docker-inventory <command>"));
    }

    @Test
    void aCommandsHelpListsItsSettings() {
        assertAll(
                () -> assertThat(run(Map.of(), "facts", "--help")).isZero(),
                () -> assertThat(out()).contains("DISCOVERY_DIR", "required", "discovery's output folder"),
                () -> assertThat(ran).isEmpty());
    }

    @Test
    void anUnknownCommandIsAUsageError() {
        assertAll(
                () -> assertThat(run(Map.of(), "fact")).isEqualTo(2),
                () -> assertThat(err()).contains("unknown command: fact"));
    }

    @Test
    void anUnknownFlagIsAUsageError() {
        assertAll(
                () -> assertThat(run(Map.of(), "facts", "--fast")).isEqualTo(2),
                () -> assertThat(err()).contains("facts: unknown argument --fast"),
                () -> assertThat(ran).isEmpty());
    }

    @Test
    void aRequiredSettingLeftOutIsAUsageErrorNamingItBeforeTheCommandRuns() {
        assertAll(
                () -> assertThat(run(Map.of("OUT_DIR", "out"), "facts")).isEqualTo(2),
                () -> assertThat(err()).contains("facts: DISCOVERY_DIR is required"),
                () -> assertThat(ran).isEmpty());
    }

    @Test
    void aCommandThatFailsExitsOneWithTheFirstLineOfWhy() {
        assertAll(
                () -> assertThat(run(Map.of(), "rules")).isEqualTo(1),
                () -> assertThat(err()).contains("rules failed: EOL_LIST /x does not exist")
                        .doesNotContain("more detail"));
    }

    @Test
    void theVersionIsPrinted() {
        assertAll(
                () -> assertThat(run(Map.of(), "--version")).isZero(),
                () -> assertThat(out()).startsWith("docker-inventory " + Version.current()));
    }

    @Test
    void aSettingThatLooksMistypedIsWarnedOfWithTheNearestRealOne() {
        run(Map.of("DISCOVERY_DIR", "d", "OUT_DIR", "o", "DISCOVERY_DIRS", "x"), "facts");

        assertThat(err()).contains("DISCOVERY_DIRS is not a setting; did you mean DISCOVERY_DIR?");
    }
}
