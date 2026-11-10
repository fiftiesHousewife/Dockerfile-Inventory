package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    void aSettingRequiredWithAnotherIsRequiredOnlyWhenThatOneIsSet() {
        Command lineage = new Command("lineage", "links", List.of(Setting.optional("ARTIFACTORY_URL", "where"),
                Setting.requiredWith("ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "ARTIFACTORY_URL", "calls a second")),
                ran::add);
        PrintStream errors = new PrintStream(err, true, StandardCharsets.UTF_8);

        assertAll(
                () -> assertThat(DockerInventory.run(lineage, List.of(), Map.of(), System.out, errors, List.of()))
                        .isZero(),
                () -> assertThat(DockerInventory.run(lineage, List.of(), Map.of("ARTIFACTORY_URL", "https://a"),
                        System.out, errors, List.of())).isEqualTo(2),
                () -> assertThat(err()).contains("lineage: ARTIFACTORY_MAX_REQUESTS_PER_SECOND is required: calls a "
                        + "second"));
    }

    @Test
    void theLaunchReadsTheFlagsTheSettingsFileAndTheLoadBeforeTheCommandRuns(@TempDir Path dir) throws IOException {
        Path settings = Files.writeString(dir.resolve("s.yaml"), "out: /data\nplatform: openshift-4-restricted\n");
        Map<String, String> seen = new HashMap<>();
        Command survey = new Command("survey", "all", List.of(), true, (args, env) -> {
            seen.putAll(env);
            seen.put("args", String.join(" ", args));
            return 0;
        });

        int exit = DockerInventory.launch(List.of("--load", "gentle", "survey", "--settings", settings.toString(),
                        "--try"), Map.of("PROFILE", "openshift-4-restricted-v2"),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8),
                List.of(survey));

        assertAll(
                () -> assertThat(exit).isZero(),
                () -> assertThat(seen).containsEntry("args", "--try").containsEntry("OUT_DIR", "/data")
                        .containsEntry("PROFILE", "openshift-4-restricted-v2")
                        .containsEntry("GHE_MAX_REQUESTS_PER_SECOND", "2"));
    }

    @Test
    void aBadSettingsFileOrLoadIsAUsageError() {
        PrintStream errors = new PrintStream(err, true, StandardCharsets.UTF_8);

        assertAll(
                () -> assertThat(DockerInventory.launch(List.of("--load", "fast", "facts"), Map.of(), System.out,
                        errors, List.of(facts))).isEqualTo(2),
                () -> assertThat(err()).contains("--load is gentle or normal, not fast"));
    }

    @Test
    void aSettingRequiredUnlessAnotherIsSetIsAUsageErrorWhenNeitherIs() {
        Command discovery = new Command("discovery", "finds", List.of(Setting.requiredUnless("GHE_TOKEN",
                "GHE_TOKEN_FILE", "a token"), Setting.optional("GHE_TOKEN_FILE", "a file")), ran::add);
        PrintStream errors = new PrintStream(err, true, StandardCharsets.UTF_8);

        assertAll(
                () -> assertThat(DockerInventory.run(discovery, List.of(), Map.of("GHE_TOKEN_FILE", "/t"), System.out,
                        errors, List.of())).isZero(),
                () -> assertThat(DockerInventory.run(discovery, List.of(), Map.of(), System.out, errors, List.of()))
                        .isEqualTo(2),
                () -> assertThat(err()).contains("discovery: GHE_TOKEN is required: a token"));
    }
}
