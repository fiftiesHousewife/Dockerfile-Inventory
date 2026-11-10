package io.github.fiftieshousewife.dockerfileinventory.survey;

import io.github.fiftieshousewife.dockerfileinventory.converter.ConverterMain;
import io.github.fiftieshousewife.dockerfileinventory.discovery.Combine;
import io.github.fiftieshousewife.dockerfileinventory.discovery.DiscoveryMain;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsMain;
import io.github.fiftieshousewife.dockerfileinventory.lineage.LineageMain;
import io.github.fiftieshousewife.dockerfileinventory.report.CatalogueMain;
import io.github.fiftieshousewife.dockerfileinventory.report.ReportMain;
import io.github.fiftieshousewife.dockerfileinventory.rules.RulesMain;
import io.github.fiftieshousewife.dockerfileinventory.validator.ValidatorMain;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Surveys organisations: {@code survey [--try] <organisation URL>...} runs every stage in order into
 * {@code out/<organisation>/} ({@code out/<organisation>-try/} for a try, which stops after ten repositories holding a
 * Dockerfile; several organisations are each discovered, merged, then surveyed as one estate in
 * {@code out/<a>+<b>/}), and says where the report is. The token comes from {@code GHE_TOKEN_FILE} or {@code GHE_TOKEN};
 * lineage runs when {@code ARTIFACTORY_URL} is set; every other stage's setting is passed on.
 */
public final class SurveyMain {

    private static final Logger LOG = LoggerFactory.getLogger(SurveyMain.class);

    static final String USAGE = "usage: survey [--try] <organisation URL>..., e.g. survey --try "
            + "https://github.com/acme";

    interface Runner {
        void run(Map<String, String> env) throws Exception;
    }

    static final Map<String, Runner> RUNNERS = Map.of(
            "discovery", DiscoveryMain::run,
            "combine", SurveyMain::combine,
            "facts", FactsMain::run,
            "lineage", LineageMain::run,
            "catalogue", CatalogueMain::run,
            "rules", RulesMain::run,
            "validator", ValidatorMain::run,
            "converter", ConverterMain::run,
            "report", ReportMain::run);

    record Arguments(List<String> urls, boolean trial) {

        Arguments {
            urls = List.copyOf(urls);
        }
    }

    static final class StageFailed extends Exception {

        private static final long serialVersionUID = 1L;

        StageFailed(String stage, Exception cause) {
            super(stage + " failed: " + String.valueOf(cause.getMessage()).lines().findFirst().orElse(""), cause);
        }
    }

    private SurveyMain() {
    }

    /**
     * Runs the survey; a stage that fails stops it, and a rerun resumes discovery where it stopped.
     *
     * @param args {@code [--try] <organisation URL>}
     */
    public static void main(String... args) {
        int exit = run(args, System.getenv(), RUNNERS, System.err);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    /**
     * Runs the survey the arguments name with the given settings.
     *
     * @param args {@code [--try] <organisation URL>...}
     * @param env the settings
     * @return 0 when the report is written, 1 when a stage failed, 2 when the arguments are wrong
     */
    public static int run(List<String> args, Map<String, String> env) {
        return run(args.toArray(String[]::new), env, RUNNERS, System.err);
    }

    static int run(String[] args, Map<String, String> env, Map<String, Runner> runners, PrintStream err) {
        Arguments arguments;
        try {
            arguments = arguments(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 2;
        }
        try {
            Path report = survey(arguments, env, runners);
            LOG.info("survey: the report is {}", report.toAbsolutePath());
            return 0;
        } catch (StageFailed e) {
            err.println("survey: " + explained(e));
            return 1;
        }
    }

    static Path survey(Arguments arguments, Map<String, String> env, Map<String, Runner> runners)
            throws StageFailed {
        List<Survey.Step> steps = Survey.steps(arguments.urls(), arguments.trial(), env);
        for (Survey.Step step : steps) {
            runStage(step, runners);
        }
        return Path.of(steps.getLast().env().get("OUT_DIR"), "index.html");
    }

    static void runStage(Survey.Step step, Map<String, Runner> runners) throws StageFailed {
        LOG.info("survey: {} into {}", step.stage(), step.env().get("OUT_DIR"));
        try {
            runners.get(step.stage()).run(step.env());
        } catch (Exception e) {
            throw new StageFailed(step.stage(), e);
        }
    }

    static String explained(StageFailed failure) {
        return failure.getMessage() + (failure.getMessage().contains(" returned 401")
                ? "; GitHub refused the token: give one that can read the organisation as GHE_TOKEN or GHE_TOKEN_FILE"
                + " (with the GitHub CLI, `env -u GITHUB_TOKEN gh auth token`, so a GITHUB_TOKEN set elsewhere is not"
                + " taken instead)" : "");
    }

    static void combine(Map<String, String> env) {
        LOG.info("{}", combined(Combine.of(Arrays.stream(env.get("COMBINE_SOURCES").split(",")).map(Path::of)
                .toList(), Path.of(env.get("OUT_DIR")))));
    }

    static String combined(Combine.Summary merged) {
        return "survey: combined " + merged.locations() + " locations, " + merged.repositories() + " repositories, "
                + merged.blobs() + " Dockerfiles" + (merged.repeated().isEmpty() ? ""
                : "; in more than one organisation, so counted twice: " + String.join(", ", merged.repeated()));
    }

    static Arguments arguments(String... args) {
        List<String> words = List.of(args);
        boolean trial = words.contains("--try");
        List<String> rest = words.stream().filter(word -> !word.equals("--try")).toList();
        if (rest.isEmpty() || rest.stream().anyMatch(word -> word.startsWith("-"))) {
            throw new IllegalArgumentException(USAGE);
        }
        return new Arguments(rest, trial);
    }
}
