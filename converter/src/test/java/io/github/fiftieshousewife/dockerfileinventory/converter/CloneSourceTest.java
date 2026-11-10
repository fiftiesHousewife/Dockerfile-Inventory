package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloneSourceTest {

    @Test
    void eachFormOfTheApiUrlClonesFromItsWebHost() {
        assertAll(
                () -> assertThat(CloneSource.from(Map.of()).url("o/r"))
                        .isEqualTo("https://github.com/o/r.git"),
                () -> assertThat(CloneSource.from(Map.of("GHE_API_URL", "https://github.com/MyOrg")).url("o/r"))
                        .isEqualTo("https://github.com/o/r.git"),
                () -> assertThat(CloneSource.from(Map.of("GHE_API_URL", "https://api.myco.ghe.com")).url("o/r"))
                        .isEqualTo("https://myco.ghe.com/o/r.git"),
                () -> assertThat(CloneSource.from(Map.of("GHE_API_URL", "https://github.example.com/api/v3"))
                        .url("o/r")).isEqualTo("https://github.example.com/o/r.git"));
    }

    @Test
    void aTokenReachesGitThroughItsEnvironmentAndWithoutOneGitOnlyNeverPrompts() {
        String basic = Base64.getEncoder().encodeToString("x-access-token:t0k".getBytes(StandardCharsets.UTF_8));

        assertAll(
                () -> assertThat(CloneSource.from(Map.of("GHE_TOKEN", "t0k")).gitEnvironment())
                        .containsExactlyInAnyOrderEntriesOf(Map.of("GIT_TERMINAL_PROMPT", "0", "GIT_CONFIG_COUNT", "1",
                                "GIT_CONFIG_KEY_0", "http.extraHeader",
                                "GIT_CONFIG_VALUE_0", "Authorization: Basic " + basic)),
                () -> assertThat(CloneSource.from(Map.of()).gitEnvironment())
                        .containsExactlyEntriesOf(Map.of("GIT_TERMINAL_PROMPT", "0")));
    }

    @Test
    void itSaysWhereItClonesFromButNeverTheToken() {
        assertThat(CloneSource.from(Map.of("GHE_TOKEN", "t0k")))
                .hasToString("https://github.com with GHE_TOKEN (variable)");
    }

    @Test
    void anApiUrlWithoutAHostIsRefusedNamingTheSetting() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CloneSource.web("file:///tmp/x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GHE_API_URL has no host: file:///tmp/x");
    }
}
