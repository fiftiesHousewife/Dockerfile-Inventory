package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("github-live")
class GitHubLiveTest {

    private static final List<String> NETWORK = List.of("HTTPS_PROXY", "HTTP_PROXY", "NO_PROXY", "TRUST_BUNDLE");

    @Test
    void restFindsWhatGraphqlFinds(@TempDir Path out) throws IOException {
        String token = System.getenv("GH_TOKEN");
        assumeTrue(token != null && !token.isBlank(), "GH_TOKEN is not set: GH_TOKEN=$(gh auth token)");
        String repository = System.getenv().getOrDefault("LIVE_REPOSITORY", "sclorg/postgresql-container");

        DiscoveryMain.run(settings(token, repository, "auto", out.resolve("graphql")));
        DiscoveryMain.run(settings(token, repository, "rest", out.resolve("rest")));

        assertThat(contents(out.resolve("rest"))).isEqualTo(contents(out.resolve("graphql")));
    }

    private static Map<String, String> settings(String token, String repository, String api, Path out) {
        Map<String, String> env = new HashMap<>();
        NETWORK.stream().filter(name -> System.getenv(name) != null)
                .forEach(name -> env.put(name, System.getenv(name)));
        env.putAll(Map.of("GHE_API_URL", "https://github.com/" + repository.split("/")[0], "GHE_TOKEN", token,
                "REPOSITORIES", repository, "GHE_API", api, "GHE_LAST_CHANGES", "true",
                "GHE_MAX_REQUESTS_PER_SECOND", "5", "OUT_DIR", out.toString()));
        return env;
    }

    private static Map<String, String> contents(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            Map<String, String> contents = new TreeMap<>();
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                contents.put(dir.relativize(file).toString(), content(file));
            }
            return contents;
        }
    }

    private static String content(Path file) throws IOException {
        return file.toString().endsWith(".jsonl")
                ? Files.readAllLines(file).stream().sorted().collect(Collectors.joining("\n"))
                : new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
    }
}
