package io.github.fiftieshousewife.dockerfileinventory.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.discovery.Combine;
import io.github.fiftieshousewife.dockerfileinventory.discovery.Confirmation;
import io.github.fiftieshousewife.dockerfileinventory.discovery.RunStopped;
import io.github.fiftieshousewife.dockerfileinventory.discovery.SelectionNotFound;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurveyMainTest {

    @Test
    void theArgumentsAreTheOrganisationsUrlAndOptionallyATry() {
        assertAll(
                () -> assertThat(SurveyMain.arguments("https://github.com/acme"))
                        .isEqualTo(new SurveyMain.Arguments(List.of("https://github.com/acme"), false)),
                () -> assertThat(SurveyMain.arguments("--try", "https://github.com/acme"))
                        .isEqualTo(new SurveyMain.Arguments(List.of("https://github.com/acme"), true)),
                () -> assertThat(SurveyMain.arguments("https://github.com/acme", "https://github.com/beta"))
                        .isEqualTo(new SurveyMain.Arguments(List.of("https://github.com/acme",
                                "https://github.com/beta"), false)));
    }

    @Test
    void anythingElseIsRefusedWithTheUsage() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(SurveyMain::arguments)
                        .withMessage(SurveyMain.USAGE),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> SurveyMain.arguments("--all", "x"))
                        .withMessage(SurveyMain.USAGE),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> SurveyMain.arguments("--try"))
                        .withMessage(SurveyMain.USAGE));
    }

    @Test
    void everyStageASurveyRunsHasARunner() {
        assertThat(SurveyMain.RUNNERS).containsKeys(Survey.steps(List.of("https://github.com/acme"), false,
                Map.of("ARTIFACTORY_URL", "x")).stream().map(Survey.Step::stage).toArray(String[]::new));
    }

    @Test
    void aSurveyRunsEachStageInOrderWithItsSettingsAndEndsAtTheReport() throws Exception {
        List<String> ran = new ArrayList<>();
        Map<String, SurveyMain.Runner> recording = new HashMap<>();
        SurveyMain.RUNNERS.keySet().forEach(stage -> recording.put(stage,
                env -> ran.add(stage + " " + env.get("OUT_DIR"))));

        Path report = SurveyMain.survey(new SurveyMain.Arguments(List.of("https://github.com/acme"), true), Map.of(),
                recording);

        assertAll(
                () -> assertThat(ran).containsExactly("discovery out/acme-try/discovery", "facts out/acme-try/facts",
                        "catalogue out/acme-try/catalogue", "rules out/acme-try/rules",
                        "validator out/acme-try/validator", "converter out/acme-try/converter",
                        "report out/acme-try/report"),
                () -> assertThat(report).isEqualTo(Path.of("out/acme-try/report/index.html")));
    }

    @Test
    void aStageThatFailsStopsTheSurveyNamingTheStageAndWhy() {
        Map<String, SurveyMain.Runner> failing = new HashMap<>();
        SurveyMain.RUNNERS.keySet().forEach(stage -> failing.put(stage, env -> {
            throw new IllegalStateException("POST https://api.github.com/graphql returned 401: {\n  \"message\": "
                    + "\"Bad credentials\"}");
        }));

        assertThatThrownBy(() -> SurveyMain.survey(new SurveyMain.Arguments(List.of("https://github.com/acme"), true),
                Map.of(), failing)).isInstanceOf(SurveyMain.StageFailed.class)
                .hasMessage("discovery failed: POST https://api.github.com/graphql returned 401: {");
    }

    @Test
    void aRefusedTokenIsExplainedWithHowToGiveAnother() {
        assertThat(SurveyMain.explained(new SurveyMain.StageFailed("discovery",
                new IllegalStateException("POST https://api.github.com/graphql returned 401: {"))))
                .contains("GitHub refused the token", "GHE_TOKEN_FILE", "env -u GITHUB_TOKEN gh auth token");
    }

    @Test
    void aRunEndsWithTheUsageOrTheFailureInOneLineAndAnExitCode() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Map<String, SurveyMain.Runner> failing = new HashMap<>();
        SurveyMain.RUNNERS.keySet().forEach(stage -> failing.put(stage, env -> {
            throw new IllegalStateException("no network");
        }));

        assertAll(
                () -> assertThat(SurveyMain.run(new String[0], Map.of(), failing, new PrintStream(err, true,
                        StandardCharsets.UTF_8))).isEqualTo(2),
                () -> assertThat(SurveyMain.run(new String[] {"https://github.com/acme"}, Map.of(), failing,
                        new PrintStream(err, true, StandardCharsets.UTF_8))).isEqualTo(1),
                () -> assertThat(err.toString(StandardCharsets.UTF_8)).contains(SurveyMain.USAGE,
                        "survey: discovery failed: no network"));
    }

    @Test
    void aStageIsRunWithItsSettings() throws Exception {
        List<Map<String, String>> given = new ArrayList<>();

        SurveyMain.runStage(new Survey.Step("facts", Map.of("OUT_DIR", "out/acme/facts")), Map.of("facts",
                given::add));

        assertThat(given).containsExactly(Map.of("OUT_DIR", "out/acme/facts"));
    }

    @Test
    void aCombinationSaysWhatItHoldsAndWhichRepositoriesItCountedTwice() {
        assertAll(
                () -> assertThat(SurveyMain.combined(new Combine.Summary(3, 2, 1, List.of())))
                        .isEqualTo("survey: combined 3 locations, 2 repositories, 1 Dockerfiles"),
                () -> assertThat(SurveyMain.combined(new Combine.Summary(3, 2, 1, List.of("acme/a", "acme/b"))))
                        .endsWith("; in more than one organisation, so counted twice: acme/a, acme/b"));
    }

    @Test
    void theOrganisationsDiscoveriesAreCombinedIntoTheOneTheStagesRead(@TempDir Path root) throws IOException {
        for (String organisation : List.of("acme", "beta")) {
            Path source = root.resolve(organisation);
            Files.createDirectories(source.resolve("blobs"));
            Files.writeString(source.resolve("manifest.jsonl"), "");
            Files.writeString(source.resolve("repos.jsonl"), "");
        }

        SurveyMain.combine(Map.of("COMBINE_SOURCES", root.resolve("acme") + "," + root.resolve("beta"), "OUT_DIR",
                root.resolve("discovery").toString()));

        assertThat(root.resolve("discovery/manifest.jsonl")).isRegularFile();
    }

    @Test
    void aSelectionMatchingNothingIsAUsageErrorAndAStopOnPurposeExitsThree() {
        PrintStream err = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        Map<String, SurveyMain.Runner> notFound = new HashMap<>(SurveyMain.RUNNERS);
        notFound.put("discovery", env -> {
            throw new SelectionNotFound("no repository payments/gone");
        });
        Map<String, SurveyMain.Runner> stopped = new HashMap<>(SurveyMain.RUNNERS);
        stopped.put("discovery", env -> {
            throw new RunStopped("stopped before fetching");
        });

        assertAll(
                () -> assertThat(SurveyMain.run(new String[] {"https://github.com/acme"}, Map.of(), notFound, err))
                        .isEqualTo(2),
                () -> assertThat(SurveyMain.run(new String[] {"https://github.com/acme"}, Map.of(), stopped, err))
                        .isEqualTo(3));
    }

    @Test
    void theConfirmationGoesToDiscovery() {
        Confirmation declining = pass -> false;

        assertThat(SurveyMain.runners(declining)).containsOnlyKeys(SurveyMain.RUNNERS.keySet());
    }
}
