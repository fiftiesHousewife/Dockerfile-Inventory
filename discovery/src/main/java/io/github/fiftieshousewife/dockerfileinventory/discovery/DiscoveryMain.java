package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Runs discovery from the environment: {@code GHE_API_URL} (the server's {@code /api} URL, or
 * {@code https://api.github.com} for github.com and Enterprise Cloud), {@code GHE_TOKEN}, {@code OUT_DIR}, and
 * optionally {@code ORGANIZATIONS} (comma-separated; default every organisation), {@code RELEASE_INDEX} (CSV path)
 * and {@code CONCURRENCY} (default 16).
 */
public final class DiscoveryMain {

    private DiscoveryMain() {
    }

    public static void main(String[] args) throws IOException {
        String releaseIndex = System.getenv("RELEASE_INDEX");
        ReleaseIndex releases = releaseIndex == null ? ReleaseIndex.empty() : ReleaseIndex.read(Path.of(releaseIndex));
        GitHub gitHub = new GitHubHttp(URI.create(required("GHE_API_URL")), required("GHE_TOKEN"));
        int concurrency = Integer.parseInt(System.getenv().getOrDefault("CONCURRENCY", "16"));
        var output = new DiscoveryOutput(Path.of(required("OUT_DIR")));
        String named = System.getenv().getOrDefault("ORGANIZATIONS", "");
        List<String> organizations = Arrays.stream(named.split(",")).map(String::strip).filter(o -> !o.isEmpty())
                .toList();
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
