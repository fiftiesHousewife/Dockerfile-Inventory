package io.github.fiftieshousewife.dockerfileinventory.cli;
import static org.junit.jupiter.api.Assertions.assertAll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsFileTest {

    @Test
    void eachChoiceBecomesItsSetting() {
        assertThat(SettingsFile.parse("""
                github:
                  url: https://ghe.example.com/payments
                  tokenFile: ~/secrets/ghe-token
                  load: gentle
                artifactory:
                  url: https://artifactory.example.com/artifactory
                  tokenFile: /secrets/artifactory
                  requestsPerSecond: 5
                  concurrency: 4
                platform: openshift-4-restricted
                out: /data/inventory
                """, "s.yaml")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "GHE_API_URL", "https://ghe.example.com/payments", "GHE_TOKEN_FILE", "~/secrets/ghe-token",
                "GHE_LOAD", "gentle", "ARTIFACTORY_URL", "https://artifactory.example.com/artifactory",
                "ARTIFACTORY_TOKEN_FILE", "/secrets/artifactory", "ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "5",
                "ARTIFACTORY_CONCURRENCY", "4", "PROFILE", "openshift-4-restricted", "OUT_DIR", "/data/inventory"));
    }

    @Test
    void lastChangesOverRestAreAskedForInTheGithubSection() {
        assertThat(SettingsFile.parse("github:\n  lastChanges: true\n", "s.yaml"))
                .containsExactlyEntriesOf(Map.of("GHE_LAST_CHANGES", "true"));
    }

    @Test
    void theApiIsChosenInTheGithubSection() {
        assertThat(SettingsFile.parse("github:\n  api: rest\n", "s.yaml"))
                .containsExactlyEntriesOf(Map.of("GHE_API", "rest"));
    }

    @Test
    void anUnknownKeyIsRefusedByNameWithTheKeysThereAre() {
        assertThatThrownBy(() -> SettingsFile.parse("github:\n  uri: x\n", "s.yaml"))
                .isInstanceOf(UsageError.class)
                .hasMessage("s.yaml: github.uri is not a setting (github.url, github.urls, github.tokenFile, "
                        + "github.load, github.requestsPerSecond, github.concurrency, github.lastChanges, github.api, "
                        + "github.app)");
    }

    @Test
    void aTokensValueIsRefusedSinceTheFileShouldNameOneHeldElsewhere() {
        assertThatThrownBy(() -> SettingsFile.parse("github:\n  token: ghp_x\n", "s.yaml"))
                .isInstanceOf(UsageError.class)
                .hasMessage("s.yaml: github.token would put a secret in the file: name a file holding it, "
                        + "github.tokenFile");
    }

    @Test
    void aFileThatIsNotYamlIsRefusedByName() {
        assertThatThrownBy(() -> SettingsFile.parse("github: [unclosed", "s.yaml"))
                .isInstanceOf(UsageError.class).hasMessageStartingWith("s.yaml is not YAML: ");
    }

    @Test
    void theEnvironmentOverridesTheFile() {
        assertThat(SettingsFile.merged(Map.of("PROFILE", "a", "OUT_DIR", "b"), Map.of("PROFILE", "c")))
                .isEqualTo(Map.of("PROFILE", "c", "OUT_DIR", "b"));
    }

    @Test
    void aGroupOfOrganisationsIsListedUnderGithubUrls() {
        assertThat(SettingsFile.parse("""
                github:
                  urls:
                    - https://ghe.example.com/acme
                    - https://ghe.example.com/beta
                """, "s.yaml")).isEqualTo(Map.of("SURVEY_URLS",
                "https://ghe.example.com/acme,https://ghe.example.com/beta"));
    }

    @Test
    void aGitHubAppIsItsIdAndTheFileHoldingItsKey() {
        assertThat(SettingsFile.parse("""
                github:
                  app:
                    id: 1234
                    privateKeyFile: ~/keys/inventory.pem
                """, "s.yaml")).isEqualTo(Map.of("GHE_APP_ID", "1234", "GHE_APP_PRIVATE_KEY_FILE",
                "~/keys/inventory.pem"));
    }

    @Test
    void aServersKeysAreItsSettingsAndOnlyGitHubHasAGroupAndAnApp() {
        assertAll(
                () -> assertThat(SettingsFile.server("GHE", "GHE_API_URL")).containsKeys("urls", "app")
                        .containsEntry("tokenFile", "GHE_TOKEN_FILE"),
                () -> assertThat(SettingsFile.server("ARTIFACTORY", "ARTIFACTORY_URL")).doesNotContainKeys("urls", "app")
                        .containsEntry("concurrency", "ARTIFACTORY_CONCURRENCY"));
    }
}
