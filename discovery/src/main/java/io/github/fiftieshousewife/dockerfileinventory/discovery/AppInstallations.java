package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;

// Where a GitHub App is installed, by organisation, and a token for each installation; called with the app's JWT.
final class AppInstallations implements OrganisationCredentials.Installations {

    private static final int PAGE_SIZE = 100;

    private final GitHubRequests app;
    private final String restPrefix;

    AppInstallations(GitHubRequests app, String restPrefix) {
        this.app = app;
        this.restPrefix = restPrefix;
    }

    @Override
    public Map<String, Long> list() {
        Map<String, Long> installations = new HashMap<>();
        for (int page = 1; ; page++) {
            JsonNode listed = app.rest(restPrefix + "app/installations?per_page=" + PAGE_SIZE + "&page=" + page)
                    .orElseThrow(() -> new GitHubException("the GitHub App's installations cannot be listed"));
            if (listed.isEmpty()) {
                return installations;
            }
            listed.forEach(installation -> installations.put(installation.path("account").path("login").asString()
                    .toLowerCase(Locale.ROOT), installation.path("id").asLong()));
        }
    }

    @Override
    public InstallationToken.Issued issue(long installation) {
        JsonNode issued = app.post(restPrefix + "app/installations/" + installation + "/access_tokens");
        return new InstallationToken.Issued(issued.path("token").asString(),
                Instant.parse(issued.path("expires_at").asString()));
    }
}
