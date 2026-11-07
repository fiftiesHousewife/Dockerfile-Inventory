package io.github.fiftieshousewife.dockerfileinventory.discovery;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Runs discovery as a pod job, configured only from the environment so the token never touches a file:
 * {@code GHE_API_URL} (a GHE Server host, {@code https://github.com/MyOrg} or {@code https://myco.ghe.com}),
 * {@code GHE_TOKEN}, {@code OUT_DIR}, and optionally {@code ORGANIZATIONS}, {@code RELEASE_INDEX}, {@code CONCURRENCY}.
 */
public final class DiscoveryMain {

    private DiscoveryMain() {
    }

    /** Scans the estate into {@code OUT_DIR}; rerun against the same directory, it resumes. */
    public static void main(String[] args) throws IOException {
        Map<String, String> env = System.getenv();
        ReleaseIndex releases = releases(env.get("RELEASE_INDEX"));
        GitHubApi api = GitHubApi.from(required(env, "GHE_API_URL"));
        HttpSettings settings = HttpSettings.from(env);
        TokenSource token = TokenSource.from(env, "GHE_TOKEN");
        System.out.println("discovery: " + api.base() + ", token from " + token + ", " + settings);
        GitHub gitHub = new GitHubHttp(api, settings, token);
        int concurrency = Integer.parseInt(env.getOrDefault("CONCURRENCY", "8"));
        var output = new DiscoveryOutput(Path.of(required(env, "OUT_DIR")));
        var discovery = new Discovery(gitHub, releases, output, concurrency,
                organizations(env.getOrDefault("ORGANIZATIONS", ""), api));
        System.out.println(discovery.run());
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
