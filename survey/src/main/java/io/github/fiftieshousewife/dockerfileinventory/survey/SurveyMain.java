package io.github.fiftieshousewife.dockerfileinventory.survey;

import io.github.fiftieshousewife.dockerfileinventory.discovery.DiscoveryMain;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsMain;
import io.github.fiftieshousewife.dockerfileinventory.lineage.LineageMain;
import io.github.fiftieshousewife.dockerfileinventory.report.CatalogueMain;
import io.github.fiftieshousewife.dockerfileinventory.report.ReportMain;
import io.github.fiftieshousewife.dockerfileinventory.rules.RulesMain;
import io.github.fiftieshousewife.dockerfileinventory.validator.ValidatorMain;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Surveys one organisation: {@code survey [--try] <organisation URL>} runs every stage in order into
 * {@code out/<organisation>/} ({@code out/<organisation>-try/} for a try, which stops after ten repositories holding a
 * Dockerfile), and says where the report is. The token comes from {@code GHE_TOKEN_FILE} or {@code GHE_TOKEN};
 * lineage runs when {@code ARTIFACTORY_URL} is set; every other stage's setting is passed on.
 */
public final class SurveyMain {

    static final String USAGE = "usage: survey [--try] <organisation URL>, e.g. survey --try https://github.com/acme";

    interface Runner {
        void run(Map<String, String> env) throws Exception;
    }

    static final Map<String, Runner> RUNNERS = Map.of(
            "discovery", DiscoveryMain::run,
            "facts", FactsMain::run,
            "lineage", LineageMain::run,
            "catalogue", CatalogueMain::run,
            "rules", RulesMain::run,
            "validator", ValidatorMain::run,
            "report", ReportMain::run);

    record Arguments(String url, boolean trial) {
    }

    private SurveyMain() {
    }

    /**
     * Runs the survey; a stage that fails stops it, and a rerun resumes discovery where it stopped.
     *
     * @param args {@code [--try] <organisation URL>}
     * @throws Exception from the stage that failed
     */
    public static void main(String... args) throws Exception {
        Arguments arguments;
        try {
            arguments = arguments(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        Path report = survey(arguments, System.getenv(), RUNNERS);
        System.out.println("survey: the report is " + report.toAbsolutePath());
    }

    static Path survey(Arguments arguments, Map<String, String> env, Map<String, Runner> runners) throws Exception {
        List<Survey.Step> steps = Survey.steps(arguments.url(), arguments.trial(), env);
        for (Survey.Step step : steps) {
            System.out.println("survey: " + step.stage() + " into " + step.env().get("OUT_DIR"));
            runners.get(step.stage()).run(step.env());
        }
        return Path.of(steps.getLast().env().get("OUT_DIR"), "index.html");
    }

    static Arguments arguments(String... args) {
        List<String> words = List.of(args);
        boolean trial = words.contains("--try");
        List<String> rest = words.stream().filter(word -> !word.equals("--try")).toList();
        if (rest.size() != 1 || rest.getFirst().startsWith("-")) {
            throw new IllegalArgumentException(USAGE);
        }
        return new Arguments(rest.getFirst(), trial);
    }
}
