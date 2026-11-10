package io.github.fiftieshousewife.dockerfileinventory.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.discovery.Combine;
import io.github.fiftieshousewife.dockerfileinventory.discovery.Confirmation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StagesTest {

    @Test
    void everyStageASurveyRunsHasARunner() {
        assertThat(Stages.RUNNERS).containsKeys(Survey.steps(List.of("https://github.com/acme"), false,
                Map.of("ARTIFACTORY_URL", "x")).stream().map(Survey.Step::stage).toArray(String[]::new));
    }

    @Test
    void aSurveyRunsEachStageInOrderWithItsSettingsAndEndsAtTheReport() throws Exception {
        List<String> ran = new ArrayList<>();
        Map<String, Stages.Runner> recording = new HashMap<>();
        Stages.RUNNERS.keySet().forEach(stage -> recording.put(stage,
                env -> ran.add(stage + " " + env.get("OUT_DIR"))));

        Path report = Stages.survey(new Arguments(List.of("https://github.com/acme"), true), Map.of(), recording);

        assertAll(
                () -> assertThat(ran).containsExactly("discovery out/acme-try/discovery", "facts out/acme-try/facts",
                        "catalogue out/acme-try/catalogue", "rules out/acme-try/rules",
                        "validator out/acme-try/validator", "converter out/acme-try/converter",
                        "report out/acme-try/report"),
                () -> assertThat(report).isEqualTo(Path.of("out/acme-try/report/index.html")));
    }

    @Test
    void aStageThatFailsStopsTheSurveyNamingTheStageAndWhy() {
        Map<String, Stages.Runner> failing = new HashMap<>();
        Stages.RUNNERS.keySet().forEach(stage -> failing.put(stage, env -> {
            throw new IllegalStateException("POST https://api.github.com/graphql returned 401: {\n  \"message\": "
                    + "\"Bad credentials\"}");
        }));

        assertThatThrownBy(() -> Stages.survey(new Arguments(List.of("https://github.com/acme"), true), Map.of(),
                failing)).isInstanceOf(StageFailed.class)
                .hasMessage("discovery failed: POST https://api.github.com/graphql returned 401: {");
    }

    @Test
    void aStageIsRunWithItsSettings() throws Exception {
        List<Map<String, String>> given = new ArrayList<>();

        Stages.runStage(new Survey.Step("facts", Map.of("OUT_DIR", "out/acme/facts")), Map.of("facts", given::add));

        assertThat(given).containsExactly(Map.of("OUT_DIR", "out/acme/facts"));
    }

    @Test
    void aCombinationSaysWhatItHoldsAndWhichRepositoriesItCountedTwice() {
        assertAll(
                () -> assertThat(Stages.combined(new Combine.Summary(3, 2, 1, List.of())))
                        .isEqualTo("survey: combined 3 locations, 2 repositories, 1 Dockerfiles"),
                () -> assertThat(Stages.combined(new Combine.Summary(3, 2, 1, List.of("acme/a", "acme/b"))))
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

        Stages.combine(Map.of("COMBINE_SOURCES", root.resolve("acme") + "," + root.resolve("beta"), "OUT_DIR",
                root.resolve("discovery").toString()));

        assertThat(root.resolve("discovery/manifest.jsonl")).isRegularFile();
    }

    @Test
    void theConfirmationGoesToDiscovery() {
        Confirmation declining = pass -> false;

        assertThat(Stages.runners(declining)).containsOnlyKeys(Stages.RUNNERS.keySet());
    }
}
