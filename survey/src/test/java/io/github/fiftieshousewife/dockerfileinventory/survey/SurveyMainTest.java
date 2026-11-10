package io.github.fiftieshousewife.dockerfileinventory.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.discovery.RunStopped;
import io.github.fiftieshousewife.dockerfileinventory.discovery.SelectionNotFound;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SurveyMainTest {

    @Test
    void aRunEndsWithTheUsageOrTheFailureInOneLineAndAnExitCode() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Map<String, Stages.Runner> failing = new HashMap<>();
        Stages.RUNNERS.keySet().forEach(stage -> failing.put(stage, env -> {
            throw new IllegalStateException("no network");
        }));

        assertAll(
                () -> assertThat(SurveyMain.run(new String[0], Map.of(), failing, new PrintStream(err, true,
                        StandardCharsets.UTF_8))).isEqualTo(2),
                () -> assertThat(SurveyMain.run(new String[] {"https://github.com/acme"}, Map.of(), failing,
                        new PrintStream(err, true, StandardCharsets.UTF_8))).isEqualTo(1),
                () -> assertThat(err.toString(StandardCharsets.UTF_8)).contains(Arguments.USAGE,
                        "survey: discovery failed: no network"));
    }

    @Test
    void aSelectionMatchingNothingIsAUsageErrorAndAStopOnPurposeExitsThree() {
        PrintStream err = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        Map<String, Stages.Runner> notFound = new HashMap<>(Stages.RUNNERS);
        notFound.put("discovery", env -> {
            throw new SelectionNotFound("no repository payments/gone");
        });
        Map<String, Stages.Runner> stopped = new HashMap<>(Stages.RUNNERS);
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
    void aStopOnPurposeIsSaidAsItWasGiven() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Map<String, Stages.Runner> stopped = new HashMap<>(Stages.RUNNERS);
        stopped.put("discovery", env -> {
            throw new RunStopped("stopped before fetching");
        });

        SurveyMain.run(new String[] {"https://github.com/acme"}, Map.of(), stopped,
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("survey: stopped before fetching"
                + System.lineSeparator());
    }

    @Test
    void wrongArgumentsGivenAsAListAreAUsageErrorBeforeAnyStageRuns() {
        assertThat(SurveyMain.run(List.of("--all"), Map.of())).isEqualTo(2);
    }

    @Test
    void theExitCodeSaysWhyTheStageStopped() {
        assertAll(
                () -> assertThat(SurveyMain.exitCode(new StageFailed("discovery", new SelectionNotFound("x"))))
                        .isEqualTo(2),
                () -> assertThat(SurveyMain.exitCode(new StageFailed("discovery", new RunStopped("x"))))
                        .isEqualTo(3),
                () -> assertThat(SurveyMain.exitCode(new StageFailed("facts", new IllegalStateException("x"))))
                        .isEqualTo(1));
    }
}
