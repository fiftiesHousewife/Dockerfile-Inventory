package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Runs discovery from the environment: {@code GHE_API_URL} (any form {@link GitHubApi} reads: a GHE Server host,
 * {@code https://github.com/MyInternalOrg}, or {@code https://myco.ghe.com}), {@code GHE_TOKEN}, {@code OUT_DIR},
 * and optionally {@code ORGANIZATIONS} (comma-separated; default the organisation in the URL, else every one),
 * {@code RELEASE_INDEX} (CSV path) and {@code CONCURRENCY} (default 16).
 */
public final class DiscoveryMain {

    private DiscoveryMain() {
    }

    public static void main(String[] args) throws IOException {
        String releaseIndex = System.getenv("RELEASE_INDEX");
        ReleaseIndex releases = releaseIndex == null ? ReleaseIndex.empty() : ReleaseIndex.read(Path.of(releaseIndex));
        GitHubApi api = GitHubApi.from(required("GHE_API_URL"));
        GitHub gitHub = new GitHubHttp(api, required("GHE_TOKEN"));
        int concurrency = Integer.parseInt(System.getenv().getOrDefault("CONCURRENCY", "16"));
        var output = new DiscoveryOutput(Path.of(required("OUT_DIR")));
        String named = System.getenv().getOrDefault("ORGANIZATIONS", "");
        List<String> organizations = Arrays.stream(named.split(",")).map(String::strip).filter(o -> !o.isEmpty())
                .toList();
        if (organizations.isEmpty() && api.organization().isPresent()) {
            organizations = List.of(api.organization().get());
        }
        System.out.println(new Discovery(gitHub, releases, output, concurrency, organizations).run());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
