package io.github.fiftieshousewife.dockerfileinventory.survey;

import io.github.fiftieshousewife.dockerfileinventory.converter.ConverterMain;
import io.github.fiftieshousewife.dockerfileinventory.discovery.Combine;
import io.github.fiftieshousewife.dockerfileinventory.discovery.Confirmation;
import io.github.fiftieshousewife.dockerfileinventory.discovery.DiscoveryMain;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsMain;
import io.github.fiftieshousewife.dockerfileinventory.lineage.LineageMain;
import io.github.fiftieshousewife.dockerfileinventory.report.CatalogueMain;
import io.github.fiftieshousewife.dockerfileinventory.report.ReportMain;
import io.github.fiftieshousewife.dockerfileinventory.rules.RulesMain;
import io.github.fiftieshousewife.dockerfileinventory.validator.ValidatorMain;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Stages {

    private static final Logger LOG = LoggerFactory.getLogger(SurveyMain.class);

    interface Runner {
        void run(Map<String, String> env) throws Exception;
    }

    static final Map<String, Runner> RUNNERS = Map.of(
            "discovery", DiscoveryMain::run,
            "combine", Stages::combine,
            "facts", FactsMain::run,
            "lineage", LineageMain::run,
            "catalogue", CatalogueMain::run,
            "rules", RulesMain::run,
            "validator", ValidatorMain::run,
            "converter", ConverterMain::run,
            "report", ReportMain::run);

    private Stages() {
    }

    static Map<String, Runner> runners(Confirmation confirmation) {
        Map<String, Runner> runners = new HashMap<>(RUNNERS);
        runners.put("discovery", env -> DiscoveryMain.run(env, confirmation));
        return runners;
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

    static void combine(Map<String, String> env) {
        LOG.info("{}", combined(Combine.of(Arrays.stream(env.get("COMBINE_SOURCES").split(",")).map(Path::of)
                .toList(), Path.of(env.get("OUT_DIR")))));
    }

    static String combined(Combine.Summary merged) {
        return "survey: combined " + merged.locations() + " locations, " + merged.repositories() + " repositories, "
                + merged.blobs() + " Dockerfiles" + (merged.repeated().isEmpty() ? ""
                : "; in more than one organisation, so counted twice: " + String.join(", ", merged.repeated()));
    }
}
