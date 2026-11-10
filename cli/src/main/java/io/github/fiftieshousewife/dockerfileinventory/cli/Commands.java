package io.github.fiftieshousewife.dockerfileinventory.cli;

import static io.github.fiftieshousewife.dockerfileinventory.cli.SettingGroups.ARTIFACTORY_RATE;
import static io.github.fiftieshousewife.dockerfileinventory.cli.SettingGroups.CATALOGUED;
import static io.github.fiftieshousewife.dockerfileinventory.cli.SettingGroups.GITHUB;
import static io.github.fiftieshousewife.dockerfileinventory.cli.SettingGroups.NETWORK;
import static io.github.fiftieshousewife.dockerfileinventory.cli.SettingGroups.SELECTION;
import static io.github.fiftieshousewife.dockerfileinventory.cli.SettingGroups.concat;

import io.github.fiftieshousewife.dockerfileinventory.converter.ConverterMain;
import io.github.fiftieshousewife.dockerfileinventory.discovery.Confirmation;
import io.github.fiftieshousewife.dockerfileinventory.discovery.DiscoveryMain;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsMain;
import io.github.fiftieshousewife.dockerfileinventory.lineage.LineageMain;
import io.github.fiftieshousewife.dockerfileinventory.report.CatalogueMain;
import io.github.fiftieshousewife.dockerfileinventory.report.ReportMain;
import io.github.fiftieshousewife.dockerfileinventory.rules.RulesMain;
import io.github.fiftieshousewife.dockerfileinventory.survey.SurveyMain;
import io.github.fiftieshousewife.dockerfileinventory.validator.ValidatorMain;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class Commands {

    static final Command DISCOVERY = new Command("discovery", "finds every Dockerfile in the organisations",
            concat(List.of(List.of(Setting.required("GHE_API_URL", "a GitHub Enterprise host with the organisation, "
                            + "https://github.com/<org> or https://<sub>.ghe.com"),
                    Setting.required("OUT_DIR", "the output folder; a rerun into it resumes"),
                    Setting.optional("ORGANIZATIONS", "only these organisations, comma-separated"),
                    Setting.optional("RELEASE_INDEX", "a file of owner/repo,commitSha lines: the released commits"),
                    Setting.optional("GHE_CONCURRENCY", "8", "repositories scanned at once"),
                    Setting.optional("TRY_REPOSITORIES", "stop once this many repositories held a Dockerfile")),
                    SELECTION, GITHUB, NETWORK)),
            env -> DiscoveryMain.run(env, asking(env)));

    static final Command FACTS = new Command("facts", "reads each Dockerfile into facts",
            List.of(Setting.required("DISCOVERY_DIR", "discovery's output folder"),
                    Setting.required("OUT_DIR", "the output folder")),
            FactsMain::run);

    static final Command LINEAGE = new Command("lineage",
            "links Artifactory's images to the Dockerfiles that built them",
            concat(List.of(List.of(Setting.required("ARTIFACTORY_URL", "Artifactory's base URL"),
                    Setting.requiredUnless("ARTIFACTORY_TOKEN", List.of("ARTIFACTORY_TOKEN_FILE"),
                            "a token that can read the Docker repositories (or ARTIFACTORY_TOKEN_FILE)"),
                    Setting.optional("ARTIFACTORY_TOKEN_FILE", "a file holding it, read again when it changes"),
                    Setting.required("FACTS_DIR", "the facts stage's output folder"),
                    Setting.required("DISCOVERY_DIR", "discovery's output folder"),
                    Setting.required("OUT_DIR", "the output folder; a rerun into it resumes"),
                    ARTIFACTORY_RATE,
                    Setting.optional("ARTIFACTORY_LOAD", "gentle or normal: fills in the rate and concurrency left "
                            + "unset"),
                    Setting.optional("ARTIFACTORY_CONCURRENCY", "16", "images read at once"),
                    Setting.optional("LINK_THRESHOLD", "the score a weak link needs to credit an image, 0 to 1"),
                    Setting.optional("LINK_MARGIN", "how far that score must beat the runner-up's, 0 to 1")),
                    NETWORK)),
            LineageMain::run);

    static final Command CATALOGUE = new Command("catalogue", "writes the catalogue of every Dockerfile",
            CATALOGUED, CatalogueMain::run);

    static final Command REPORT = new Command("report", "writes the catalogue and the HTML report",
            concat(List.of(CATALOGUED, List.of(Setting.optional("VALIDATOR_DIR", "the validator's output folder, "
                    + "for the action plan page")))),
            ReportMain::run);

    static final Command RULES = new Command("rules", "runs the quality rules over every Dockerfile",
            List.of(Setting.required("DISCOVERY_DIR", "discovery's output folder"),
                    Setting.optional("LINEAGE_DIR", "lineage's output folder, for the rules about inherited users"),
                    Setting.optional("CATALOGUE_DIR", "the catalogue's output folder, for R4"),
                    Setting.optional("ALLOW_LIST", "a CSV of approved bases, imagePath,tagPrefix (S1)"),
                    Setting.optional("EOL_LIST", "a CSV of bases past their end of life (R2)"),
                    Setting.required("OUT_DIR", "the output folder")),
            RulesMain::run);

    static final Command VALIDATOR = new Command("validator", "judges each image and workload on a platform",
            List.of(Setting.required("FACTS_DIR", "the facts stage's output folder"),
                    Setting.required("DISCOVERY_DIR", "discovery's output folder"),
                    Setting.optional("PROFILE", "openshift-4-restricted-v2", "a shipped profile's name or a file"),
                    Setting.optional("LINEAGE_DIR", "lineage's output folder: users, containers to Dockerfiles"),
                    Setting.optional("CATALOGUE_DIR", "the catalogue's output folder, for fixes and their reach"),
                    Setting.optional("RULES_DIR", "the rules' output folder, whose findings become actions"),
                    Setting.required("OUT_DIR", "the output folder")),
            ValidatorMain::run);

    static final Command CONVERTER = new Command("converter", "converts each Dockerfile to the model and back",
            List.of(Setting.required("DISCOVERY_DIR", "discovery's output folder"),
                    Setting.optional("OPT_IN", "a file of owner/repo lines: only these get Java builder classes"),
                    Setting.optional("ALLOW_LIST", "approved bases, for the classes' TODOs"),
                    Setting.optional("JAVA_ROUND_TRIP", "true", "false skips compiling a class per Dockerfile"),
                    Setting.required("OUT_DIR", "the output folder")),
            ConverterMain::run);

    static final Command SURVEY = new Command("survey",
            "runs every stage in order: survey [--try] <organisation URL>...",
            concat(List.of(SELECTION, GITHUB, List.of(
                    Setting.optional("ARTIFACTORY_URL", "Artifactory's base URL; lineage runs when set"),
                    Setting.requiredWith(ARTIFACTORY_RATE.name(), "ARTIFACTORY_URL", ARTIFACTORY_RATE.description()),
                    Setting.optional("OUT_DIR", "out/<organisation>", "the output folder"),
                    Setting.optional("SURVEY_URLS", "the organisations to survey as one estate when given none, "
                            + "comma-separated (github.urls)")), NETWORK)),
            true, (args, env) -> SurveyMain.run(surveyArguments(args, env), env, asking(env)));

    static final List<Command> ALL = List.of(SURVEY, DISCOVERY, FACTS, LINEAGE, CATALOGUE, REPORT, RULES, VALIDATOR,
            CONVERTER);

    private Commands() {
    }

    static Confirmation asking(Map<String, String> env) {
        return Prompt.confirmation(env, System.console() != null, System.out);
    }

    static List<String> surveyArguments(List<String> args, Map<String, String> env) {
        boolean named = args.stream().anyMatch(arg -> !arg.startsWith("-"));
        List<String> urls = Stream.of(env.getOrDefault("SURVEY_URLS", env.getOrDefault("GHE_API_URL", ""))
                .split(",")).map(String::strip).filter(url -> !url.isEmpty()).toList();
        return named ? args : Stream.concat(args.stream(), urls.stream()).toList();
    }
}
