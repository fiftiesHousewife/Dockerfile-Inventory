package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubAccessTest {

    private static final GitHubApi API = GitHubApi.from("https://github.com/acme");

    private static OrganisationCredentials credentials(Map<String, String> env) {
        return GitHubAccess.credentials(env, API, HttpSettings.from(Map.of()), Throttle.NONE);
    }

    @Test
    void aTokenAloneReadsEveryOrganisation() {
        assertThat(credentials(Map.of("GHE_TOKEN", "t")).of("acme").token()).isEqualTo("t");
    }

    @Test
    void neitherATokenNorAnAppIsRefused() {
        assertThatThrownBy(() -> credentials(Map.of())).hasMessage("set GHE_TOKEN or GHE_TOKEN_FILE, or GHE_APP_ID "
                + "with GHE_APP_PRIVATE_KEY_FILE");
    }

    @Test
    void anAppWithoutItsKeyIsRefusedByName() {
        assertThatThrownBy(() -> credentials(Map.of("GHE_APP_ID", "1234")))
                .hasMessage("GHE_APP_ID is set without GHE_APP_PRIVATE_KEY_FILE, the app's private key");
    }

    @Test
    void anAppsKeyIsReadFromItsFile(@TempDir Path dir) throws IOException {
        Path key = Files.writeString(dir.resolve("app.pem"), AppJwtTest.pem("RSA PRIVATE KEY", AppJwtTest.pkcs1()));

        assertThat(GitHubAccess.app(Map.of("GHE_APP_ID", "1234", "GHE_APP_PRIVATE_KEY_FILE", key.toString()), API,
                HttpSettings.from(Map.of()), Throttle.NONE)).isPresent();
    }

    @Test
    void eachOrganisationsCredentialIsRecordedOneALine() {
        Map<String, String> chosen = new LinkedHashMap<>();
        chosen.put("pay", "the GitHub App, installation 7");
        chosen.put("web", "GHE_TOKEN_FILE /secrets/t");

        assertAll(
                () -> assertThat(GitHubAccess.lines(chosen)).isEqualTo(
                        "{\"organization\":\"pay\",\"credential\":\"the GitHub App, installation 7\"}\n"
                                + "{\"organization\":\"web\",\"credential\":\"GHE_TOKEN_FILE /secrets/t\"}\n"),
                () -> assertThat(GitHubAccess.lines(Map.of())).isEmpty());
    }
}
