package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.Credential;
import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

final class GitHubAccess {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubAccess.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private GitHubAccess() {
    }

    static OrganisationCredentials credentials(Map<String, String> env, GitHubApi api, HttpSettings settings,
                                               Throttle throttle) {
        Optional<Credential> token = token(env);
        Optional<OrganisationCredentials.Installations> app = app(env, api, settings, throttle);
        if (token.isEmpty() && app.isEmpty()) {
            throw new IllegalStateException("set GHE_TOKEN or GHE_TOKEN_FILE, or GHE_APP_ID with "
                    + "GHE_APP_PRIVATE_KEY_FILE");
        }
        return new OrganisationCredentials(app, token, Instant::now);
    }

    static Optional<Credential> token(Map<String, String> env) {
        return present(env, "GHE_TOKEN") || present(env, "GHE_TOKEN_FILE")
                ? Optional.of(TokenSource.from(env, "GHE_TOKEN")) : Optional.empty();
    }

    static Optional<OrganisationCredentials.Installations> app(Map<String, String> env, GitHubApi api,
                                                               HttpSettings settings, Throttle throttle) {
        if (!present(env, "GHE_APP_ID")) {
            return Optional.empty();
        }
        if (!present(env, "GHE_APP_PRIVATE_KEY_FILE")) {
            throw new IllegalStateException("GHE_APP_ID is set without GHE_APP_PRIVATE_KEY_FILE, the app's private "
                    + "key");
        }
        AppCredential app = new AppCredential(env.get("GHE_APP_ID").strip(), AppJwt.key(read(Path.of(
                env.get("GHE_APP_PRIVATE_KEY_FILE")))), Instant::now);
        return Optional.of(new AppInstallations(new GitHubRequests(new GitHubSender(settings.client(), app,
                settings.retries(), Sleeper.REAL, throttle), api.base(), app, settings.requestTimeout()),
                api.restPrefix()));
    }

    static GitHub client(GitHubApi api, HttpSettings settings, Throttle throttle, OrganisationCredentials credentials,
                         boolean lastChanges) {
        Map<Credential, GitHub> clients = new ConcurrentHashMap<>();
        Function<Credential, GitHub> client = credential -> clients.computeIfAbsent(credential, c -> chosen(
                new GitHubRequests(new GitHubSender(settings.client(), c, settings.retries(), Sleeper.REAL, throttle),
                        api.base(), c, settings.requestTimeout()), api.restPrefix(), lastChanges, c.toString()));
        return new PerOrganisation(org -> client.apply(credentials.of(org)),
                () -> credentials.token().map(t -> client.apply(t).organizations()).orElseGet(credentials::installed));
    }

    static GitHub chosen(GitHubRequests requests, String restPrefix, boolean lastChanges, String credential) {
        if (requests.graphqlAllowed()) {
            return new GitHubHttp(requests, restPrefix);
        }
        LOG.info("discovery: {} may not use GraphQL, so its organisations are read over REST, a call a blob{}",
                credential, lastChanges ? " and a location"
                        : ", without last changes (GHE_LAST_CHANGES=true reads them)");
        return new GitHubRest(requests, restPrefix, lastChanges);
    }

    static String lines(Map<String, String> chosen) {
        return chosen.entrySet().stream().map(e -> JSON.createObjectNode().put("organization", e.getKey())
                .put("credential", e.getValue()).toString() + "\n").collect(Collectors.joining());
    }

    static boolean present(Map<String, String> env, String name) {
        return env.get(name) != null && !env.get(name).isBlank();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("GHE_APP_PRIVATE_KEY_FILE cannot be read: " + file, e);
        }
    }
}
