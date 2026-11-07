package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// GitHub Enterprise Server serves REST under /api/v3/ and GraphQL at /api/graphql on its own host; github.com
// (Enterprise Cloud too) and data-residency <sub>.ghe.com serve REST at the root of api.<host> and GraphQL at /graphql.
record GitHubApi(URI base, String restPrefix, Optional<String> organization) {

    private static final String DOT_COM = "github.com";
    private static final String CLOUD_SUFFIX = ".ghe.com";

    static GitHubApi from(String url) {
        URI uri = URI.create(url.strip());
        String host = uri.getHost();
        List<String> path = segments(uri);
        if (host.equals(DOT_COM) || host.equals("api." + DOT_COM)) {
            return cloud(DOT_COM, host.equals(DOT_COM) ? first(path) : Optional.empty());
        }
        if (host.endsWith(CLOUD_SUFFIX)) {
            boolean api = host.startsWith("api.");
            return cloud(api ? host.substring(4) : host, api ? Optional.empty() : first(path));
        }
        String authority = uri.getScheme() + "://" + uri.getRawAuthority();
        Optional<String> organization = first(path).filter(segment -> !segment.equals("api"));
        return new GitHubApi(URI.create(authority + "/api/"), "v3/", organization);
    }

    static List<String> segments(URI uri) {
        return Arrays.stream(uri.getPath() == null ? new String[0] : uri.getPath().split("/"))
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    private static GitHubApi cloud(String host, Optional<String> organization) {
        return new GitHubApi(URI.create("https://api." + host + "/"), "", organization);
    }

    private static Optional<String> first(List<String> path) {
        return path.stream().findFirst();
    }
}
