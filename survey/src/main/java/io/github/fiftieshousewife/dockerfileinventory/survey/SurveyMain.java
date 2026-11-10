package io.github.fiftieshousewife.dockerfileinventory.survey;

import io.github.fiftieshousewife.dockerfileinventory.discovery.Confirmation;
import io.github.fiftieshousewife.dockerfileinventory.discovery.RunStopped;
import io.github.fiftieshousewife.dockerfileinventory.discovery.SelectionNotFound;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Surveys organisations: {@code survey [--try] <organisation URL>...} runs every stage in order into
 * {@code out/<organisation>/} ({@code out/<organisation>-try/} for a try, which stops after ten repositories holding a
 * Dockerfile; several organisations are each discovered, merged, then surveyed as one estate in
 * {@code out/<a>+<b>/}), and says where the report is. The token comes from {@code GHE_TOKEN_FILE} or
 * {@code GHE_TOKEN}; lineage runs when {@code ARTIFACTORY_URL} is set; every other stage's setting is passed on.
 */
public final class SurveyMain {

    private static final Logger LOG = LoggerFactory.getLogger(SurveyMain.class);

    private SurveyMain() {
    }

    /**
     * Runs the survey; a stage that fails stops it, and a rerun resumes discovery where it stopped.
     *
     * @param args {@code [--try] <organisation URL>}
     */
    public static void main(String... args) {
        int exit = run(args, System.getenv(), Stages.RUNNERS, System.err);
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
        return run(args, env, Confirmation.ALWAYS);
    }

    /**
     * Runs the survey, asking after discovery's first pass whether to fetch.
     *
     * @param args {@code [--try] <organisation URL>...}
     * @param env the settings
     * @param confirmation asked once discovery has listed the repositories and walked their trees
     * @return 0 when the report is written, 1 when a stage failed, 2 when the arguments or the selection are wrong,
     *     3 when stopped on purpose
     */
    public static int run(List<String> args, Map<String, String> env, Confirmation confirmation) {
        return run(args.toArray(String[]::new), env, Stages.runners(confirmation), System.err);
    }

    static int run(String[] args, Map<String, String> env, Map<String, Stages.Runner> runners, PrintStream err) {
        Arguments arguments;
        try {
            arguments = Arguments.of(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 2;
        }
        try {
            Path report = Stages.survey(arguments, env, runners);
            LOG.info("survey: the report is {}", report.toAbsolutePath());
            return 0;
        } catch (StageFailed e) {
            err.println("survey: " + (e.getCause() instanceof RunStopped stop ? stop.getMessage() : e.explained()));
            return exitCode(e);
        }
    }

    static int exitCode(StageFailed failure) {
        return switch (failure.getCause()) {
            case SelectionNotFound notFound -> 2;
            case RunStopped stopped -> 3;
            default -> 1;
        };
    }
}
