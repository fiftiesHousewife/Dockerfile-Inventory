package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.util.List;

final class SettingGroups {

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
                    + "gives it (PEM)"),
            Setting.optional("GHE_LAST_CHANGES", "false", "true to read each location's last change where GraphQL "
                    + "is refused and REST is used: a call a location"));

    static final List<Setting> CATALOGUED = List.of(
            Setting.required("FACTS_DIR", "the facts stage's output folder"),
            Setting.required("DISCOVERY_DIR", "discovery's output folder"),
            Setting.optional("LINEAGE_DIR", "lineage's output folder: registry images, chains and labels"),
            Setting.optional("CONVERTER_DIR", "the converter's output folder: each Dockerfile's round trip"),
            Setting.optional("STALE_AFTER_MONTHS", "an image unpublished this long is unmaintained"),
            Setting.optional("LAYER_LIMIT", "an image with more layers is flagged"),
            Setting.optional("FOOTPRINTS", "a CSV of footprint rules tried before the built-in ones"),
            Setting.required("OUT_DIR", "the output folder"));

    private SettingGroups() {
    }

    static List<Setting> concat(List<List<Setting>> lists) {
        return lists.stream().flatMap(List::stream).toList();
    }
}
