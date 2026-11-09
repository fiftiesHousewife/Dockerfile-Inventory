package io.github.fiftieshousewife.dockerfileinventory.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SurveyMainTest {

    @Test
    void theArgumentsAreTheOrganisationsUrlAndOptionallyATry() {
        assertAll(
                () -> assertThat(SurveyMain.arguments("https://github.com/acme"))
                        .isEqualTo(new SurveyMain.Arguments("https://github.com/acme", false)),
                () -> assertThat(SurveyMain.arguments("--try", "https://github.com/acme"))
                        .isEqualTo(new SurveyMain.Arguments("https://github.com/acme", true)));
    }

    @Test
    void anythingElseIsRefusedWithTheUsage() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(SurveyMain::arguments)
                        .withMessage(SurveyMain.USAGE),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> SurveyMain.arguments("--all", "x"))
                        .withMessage(SurveyMain.USAGE));
    }

    @Test
    void everyStageASurveyRunsHasARunner() {
        assertThat(SurveyMain.RUNNERS).containsKeys(Survey.steps("https://github.com/acme", false,
                Map.of("ARTIFACTORY_URL", "x")).stream().map(Survey.Step::stage).toArray(String[]::new));
    }

    @Test
    void aSurveyRunsEachStageInOrderWithItsSettingsAndEndsAtTheReport() throws Exception {
        List<String> ran = new ArrayList<>();
        Map<String, SurveyMain.Runner> recording = new HashMap<>();
        SurveyMain.RUNNERS.keySet().forEach(stage -> recording.put(stage,
                env -> ran.add(stage + " " + env.get("OUT_DIR"))));

        Path report = SurveyMain.survey(new SurveyMain.Arguments("https://github.com/acme", true), Map.of(),
                recording);

        assertAll(
                () -> assertThat(ran).containsExactly("discovery out/acme-try/discovery", "facts out/acme-try/facts",
                        "catalogue out/acme-try/catalogue", "rules out/acme-try/rules",
                        "validator out/acme-try/validator", "report out/acme-try/report"),
                () -> assertThat(report).isEqualTo(Path.of("out/acme-try/report/index.html")));
    }
}
