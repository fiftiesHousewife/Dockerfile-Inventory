package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class OrganisationCredentialsTest {

    private static final TokenSource TOKEN = TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN");

    private final AtomicInteger listed = new AtomicInteger();
    private final List<Long> issued = new ArrayList<>();
    private final OrganisationCredentials.Installations app = new OrganisationCredentials.Installations() {
        @Override
        public Map<String, Long> list() {
            listed.incrementAndGet();
            return Map.of("pay", 7L);
        }

        @Override
        public InstallationToken.Issued issue(long installation) {
            issued.add(installation);
            return new InstallationToken.Issued("ghs_" + installation, Instant.MAX);
        }
    };

    @Test
    void anOrganisationWithTheAppIsReadWithItsInstallationAndTheRestWithTheToken() {
        OrganisationCredentials credentials = new OrganisationCredentials(Optional.of(app), Optional.of(TOKEN),
                Instant::now);

        assertAll(
                () -> assertThat(credentials.of("Pay").token()).isEqualTo("ghs_7"),
                () -> assertThat(credentials.of("web")).isSameAs(TOKEN),
                () -> assertThat(credentials.chosen()).containsExactly(
                        Map.entry("pay", "the GitHub App, installation 7"),
                        Map.entry("web", "GHE_TOKEN (variable)")));
    }

    @Test
    void theInstallationsAreListedOnceAndAChoiceIsKept() {
        OrganisationCredentials credentials = new OrganisationCredentials(Optional.of(app), Optional.of(TOKEN),
                Instant::now);
        Credential first = credentials.of("pay");

        assertAll(
                () -> assertThat(credentials.of("pay")).isSameAs(first),
                () -> assertThat(credentials.of("web")).isSameAs(TOKEN),
                () -> assertThat(listed.get()).isEqualTo(1));
    }

    @Test
    void anOrganisationNeitherReachesStopsTheRunNamingIt() {
        assertThatThrownBy(() -> new OrganisationCredentials(Optional.of(app), Optional.empty(), Instant::now)
                .of("web")).hasMessage("no credential reads web: the GitHub App is not installed there, and neither "
                + "GHE_TOKEN nor GHE_TOKEN_FILE is set");
    }

    @Test
    void theOrganisationsReachedAreTheTokensElseTheAppsInstallations() {
        assertThat(new OrganisationCredentials(Optional.of(app), Optional.empty(), Instant::now).installed())
                .containsExactly("pay");
    }
}
