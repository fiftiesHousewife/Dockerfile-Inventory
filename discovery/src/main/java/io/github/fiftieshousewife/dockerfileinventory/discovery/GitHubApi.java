package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Where a GitHub's API lives, worked out from whatever URL people know it by:
 * <ul>
 *   <li>GitHub Enterprise Server on its own host ({@code https://github.mycompany.com}, with or without
 *       {@code /api} or {@code /api/v3}): REST under {@code /api/v3/}, GraphQL at {@code /api/graphql}.</li>
 *   <li>github.com, which Enterprise Cloud also uses ({@code https://github.com/MyInternalOrg}): REST at the root
 *       of {@code api.github.com}, GraphQL at {@code /graphql}.</li>
 *   <li>Enterprise Cloud with data residency ({@code https://myco.ghe.com}): the same, at
 *       {@code api.myco.ghe.com}.</li>
 * </ul>
 * An organisation in the path ({@code github.com/MyInternalOrg}) is the one to scan.
 */
record GitHubApi(URI base, String restPrefix, Optional<String> organization) {

    private static final String DOT_COM = "github.com";
    private static final String CLOUD_SUFFIX = ".ghe.com";

    static GitHubApi from(String url) {
        URI uri = URI.create(url.strip());
        String host = uri.getHost();
        List<String> path = Arrays.stream(uri.getPath() == null ? new String[0] : uri.getPath().split("/"))
                .filter(segment -> !segment.isEmpty())
                .toList();
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

    private static GitHubApi cloud(String host, Optional<String> organization) {
        return new GitHubApi(URI.create("https://api." + host + "/"), "", organization);
    }

    private static Optional<String> first(List<String> path) {
        return path.stream().findFirst();
    }
}
