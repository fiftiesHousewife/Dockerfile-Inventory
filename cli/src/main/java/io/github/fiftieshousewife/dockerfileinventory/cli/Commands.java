package io.github.fiftieshousewife.dockerfileinventory.cli;

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

// The settings each command reads, as the README lists them; required ones are checked before the command runs.
final class Commands {

    static final List<Setting> NETWORK = List.of(
            Setting.optional("HTTPS_PROXY", "a proxy for HTTPS (HTTP_PROXY and NO_PROXY too)"),
            Setting.optional("HTTP_PROXY", "a proxy for HTTP"),
            Setting.optional("NO_PROXY", "hosts reached without the proxy"),
            Setting.optional("TRUST_BUNDLE", "a PEM file of the CAs to trust beside the JDK's"),
            Setting.optional("CONNECT_TIMEOUT", "10s", "how long to wait for a connection"),
            Setting.optional("HTTP_TIMEOUT", "60s", "how long to wait for a response"),
            Setting.optional("HTTP_RETRIES", "5", "attempts after a network error or a gateway's 502, 503 or 504"),
            Setting.optional("MAX_RATE_LIMIT_WAIT", "1h", "the longest wait for a rate limit to reset"));

    static final Setting GITHUB_RATE = Setting.required("GHE_MAX_REQUESTS_PER_SECOND", "the most calls to GitHub a "
            + "second; or --load gentle (2) or normal (10)");
    static final Setting ARTIFACTORY_RATE = Setting.required("ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "the most calls "
            + "to Artifactory a second; or --load gentle (5) or normal (20)");

    static final List<Setting> SELECTION = List.of(
            Setting.optional("REPOSITORIES", "only these repositories, owner/name, comma-separated (--repository)"),
            Setting.optional("REPOSITORY_NAME_CONTAINS", "only repositories whose name holds one of these, "
                    + "comma-separated (--repository-name-contains)"),
            Setting.optional("MAX_DOCKERFILES", "stop after the first pass when it finds more distinct Dockerfiles "
                    + "(--max-dockerfiles)"),
            Setting.optional("ASSUME_YES", "true goes on after the first pass without asking (--yes)"));

    static final List<Setting> GITHUB = List.of(GITHUB_RATE,
            Setting.optional("GHE_LOAD", "gentle or normal: fills in the rate and concurrency left unset"),
            Setting.requiredUnless("GHE_TOKEN", List.of("GHE_TOKEN_FILE", "GHE_APP_ID"), "a token that can read "
                    + "the organisations (or GHE_TOKEN_FILE, or a GitHub App)"),
            Setting.optional("GHE_TOKEN_FILE", "a file holding it, read again when it changes"),
            Setting.optional("GHE_APP_ID", "a GitHub App's ID: organisations where it is installed are read with it, "
                    + "the rest with the token"),
            Setting.requiredWith("GHE_APP_PRIVATE_KEY_FILE", "GHE_APP_ID", "the GitHub App's private key, as GitHub "
                    + "gives it (PEM)"));

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

    static final List<Setting> CATALOGUED = List.of(
            Setting.required("FACTS_DIR", "the facts stage's output folder"),
            Setting.required("DISCOVERY_DIR", "discovery's output folder"),
            Setting.optional("LINEAGE_DIR", "lineage's output folder: registry images, chains and labels"),
            Setting.optional("CONVERTER_DIR", "the converter's output folder: each Dockerfile's round trip"),
            Setting.optional("STALE_AFTER_MONTHS", "an image unpublished this long is unmaintained"),
            Setting.optional("LAYER_LIMIT", "an image with more layers is flagged"),
            Setting.optional("FOOTPRINTS", "a CSV of footprint rules tried before the built-in ones"),
            Setting.required("OUT_DIR", "the output folder"));

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

    // A survey given no organisation surveys the settings file's group (github.urls), else its github.url.
    static List<String> surveyArguments(List<String> args, Map<String, String> env) {
        boolean named = args.stream().anyMatch(arg -> !arg.startsWith("-"));
        List<String> urls = Stream.of(env.getOrDefault("SURVEY_URLS", env.getOrDefault("GHE_API_URL", ""))
                .split(",")).map(String::strip).filter(url -> !url.isEmpty()).toList();
        return named ? args : Stream.concat(args.stream(), urls.stream()).toList();
    }

    static List<Setting> concat(List<List<Setting>> lists) {
        return lists.stream().flatMap(List::stream).toList();
    }
}
