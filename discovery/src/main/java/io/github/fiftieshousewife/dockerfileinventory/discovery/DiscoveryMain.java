package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs discovery, configured from the environment: {@code GHE_API_URL} (a GHE Server host,
 * {@code https://github.com/MyOrg} or {@code https://myco.ghe.com}); {@code GHE_TOKEN} or {@code GHE_TOKEN_FILE}, or
 * {@code GHE_APP_ID} with {@code GHE_APP_PRIVATE_KEY_FILE}, or both (each organisation is read with the app where it
 * is installed, the token elsewhere); {@code GHE_MAX_REQUESTS_PER_SECOND}; {@code OUT_DIR}; and optionally
 * {@code ORGANIZATIONS}, {@code RELEASE_INDEX}, {@code GHE_CONCURRENCY} and {@code TRY_REPOSITORIES} (stop once that
 * many repositories held a Dockerfile).
 */
public final class DiscoveryMain {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryMain.class);

    private DiscoveryMain() {
    }

    /** Scans the estate into {@code OUT_DIR}; rerun against the same directory, it resumes. */
    public static void main(String[] args) throws IOException {
        run(System.getenv());
    }

    /**
     * Scans the estate with the given settings, as {@link #main} does with the environment's.
     *
     * @param env the settings
     * @throws IOException when the release index cannot be read
     */
    public static void run(Map<String, String> env) throws IOException {
        run(env, Confirmation.ALWAYS);
    }

    /**
     * Scans the estate, asking after the first pass whether to fetch.
     *
     * @param env the settings
     * @param confirmation asked once the listing and tree walks are done
     * @throws IOException when the release index cannot be read
     */
    public static void run(Map<String, String> env, Confirmation confirmation) throws IOException {
        ReleaseIndex releases = releases(env.get("RELEASE_INDEX"));
        GitHubApi api = GitHubApi.from(required(env, "GHE_API_URL"));
        HttpSettings settings = HttpSettings.from(env);
        Throttle throttle = Throttle.of(Throttle.rate(env, "GHE_MAX_REQUESTS_PER_SECOND"));
        OrganisationCredentials credentials = GitHubAccess.credentials(env, api, settings, throttle);
        LOG.info("discovery: {}, {}", api.base(), settings);
        GitHub gitHub = GitHubAccess.client(api, settings, throttle, credentials,
                ApiChoice.from(env));
        int concurrency = Throttle.concurrency(env, "GHE_CONCURRENCY", 8);
        var output = new DiscoveryOutput(Path.of(required(env, "OUT_DIR")));
        Progress progress = new Progress(throttle::calls, Instant::now);
        var discovery = new Discovery(gitHub, releases, output, concurrency,
                organizations(env.getOrDefault("ORGANIZATIONS", ""), api), tryRepositories(env), Selection.from(env),
                maxDockerfiles(env), confirmation, progress);
        ProgressLog progressLog = new ProgressLog(progress, Duration.ofSeconds(30), LOG::info);
        try {
            LOG.info("discovery: {}", discovery.run());
        } finally {
            progressLog.close();
            credentials.chosen().forEach((org, how) -> LOG.info("discovery: {} read with {}", org, how));
            Files.writeString(output.dir().resolve("credentials.jsonl"), GitHubAccess.lines(credentials.chosen()));
        }
    }

    static int maxDockerfiles(Map<String, String> env) {
        String value = env.get("MAX_DOCKERFILES");
        if (value == null || value.isBlank()) {
            return 0;
        }
        if (!value.strip().matches("[1-9][0-9]{0,8}")) {
            throw new IllegalStateException("MAX_DOCKERFILES must be a positive whole number, not " + value);
        }
        return Integer.parseInt(value.strip());
    }

    static int tryRepositories(Map<String, String> env) {
        String value = env.get("TRY_REPOSITORIES");
        if (value == null) {
            return 0;
        }
        if (!value.matches("[1-9][0-9]{0,6}")) {
            throw new IllegalStateException("TRY_REPOSITORIES must be a positive whole number, not " + value);
        }
        return Integer.parseInt(value);
    }

    static ReleaseIndex releases(String path) throws IOException {
        return path == null ? ReleaseIndex.empty() : ReleaseIndex.read(Path.of(path));
    }

    static List<String> organizations(String named, GitHubApi api) {
        List<String> listed = Arrays.stream(named.split(",")).map(String::strip).filter(o -> !o.isEmpty()).toList();
        return listed.isEmpty() ? api.organization().stream().toList() : listed;
    }

    static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
