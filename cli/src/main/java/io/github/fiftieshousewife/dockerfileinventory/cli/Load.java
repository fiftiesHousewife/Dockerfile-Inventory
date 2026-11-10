package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// A load fills in each server's rate and concurrency where neither is given; choosing one counts as setting the rate.
enum Load {
    GENTLE(2, 2, 5, 4),
    NORMAL(10, 8, 20, 16);

    record Server(String prefix, int rate, int concurrency) {
    }

    private final int githubRate;
    private final int githubConcurrency;
    private final int artifactoryRate;
    private final int artifactoryConcurrency;

    Load(int githubRate, int githubConcurrency, int artifactoryRate, int artifactoryConcurrency) {
        this.githubRate = githubRate;
        this.githubConcurrency = githubConcurrency;
        this.artifactoryRate = artifactoryRate;
        this.artifactoryConcurrency = artifactoryConcurrency;
    }

    List<Server> servers() {
        return List.of(new Server("GHE", githubRate, githubConcurrency),
                new Server("ARTIFACTORY", artifactoryRate, artifactoryConcurrency));
    }

    static Map<String, String> expanded(Map<String, String> env, Optional<String> flag) {
        Map<String, String> all = new HashMap<>(env);
        List.of("GHE", "ARTIFACTORY").forEach(prefix -> flag.or(() -> Optional.ofNullable(env.get(prefix + "_LOAD")))
                .map(name -> named(name, flag.isPresent() ? "--load" : prefix + "_LOAD"))
                .flatMap(load -> load.servers().stream().filter(server -> server.prefix().equals(prefix)).findFirst())
                .ifPresent(server -> {
                    all.putIfAbsent(prefix + "_MAX_REQUESTS_PER_SECOND", String.valueOf(server.rate()));
                    all.putIfAbsent(prefix + "_CONCURRENCY", String.valueOf(server.concurrency()));
                }));
        return all;
    }

    static Load named(String name, String setting) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UsageError(setting + " is gentle or normal, not " + name);
        }
    }
}
