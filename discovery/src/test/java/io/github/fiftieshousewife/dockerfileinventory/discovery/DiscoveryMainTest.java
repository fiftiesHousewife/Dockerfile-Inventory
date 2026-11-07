package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryMainTest {

    @TempDir
    Path dir;

    @Test
    void namedOrganisationsWinOverTheOneInTheUrl() {
        assertThat(DiscoveryMain.organizations(" pay , ops,,", GitHubApi.from("https://github.com/Platform")))
                .containsExactly("pay", "ops");
    }

    @Test
    void fallsBackToTheOrganisationInTheUrl() {
        assertThat(DiscoveryMain.organizations("", GitHubApi.from("https://github.com/Platform")))
                .containsExactly("Platform");
    }

    @Test
    void scansEveryOrganisationWhenNoneIsNamed() {
        assertThat(DiscoveryMain.organizations("", GitHubApi.from("https://github.mycompany.com"))).isEmpty();
    }

    @Test
    void refusesAMissingOrBlankSetting() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DiscoveryMain.required(Map.of("GHE_TOKEN", " "), "GHE_TOKEN"))
                .withMessage("GHE_TOKEN is not set");
        assertThatIllegalStateException()
                .isThrownBy(() -> DiscoveryMain.required(Map.of(), "OUT_DIR"))
                .withMessage("OUT_DIR is not set");
    }

    @Test
    void readsASetSetting() {
        assertThat(DiscoveryMain.required(Map.of("OUT_DIR", "out"), "OUT_DIR")).isEqualTo("out");
    }

    @Test
    void readsTheReleaseIndexOnlyWhenOneIsGiven() throws IOException {
        Path csv = Files.writeString(dir.resolve("releases.csv"), "pay/api," + "a".repeat(40) + "\n");

        assertThat(DiscoveryMain.releases(null).size()).isZero();
        assertThat(DiscoveryMain.releases(csv.toString()).size()).isEqualTo(1);
    }
}
