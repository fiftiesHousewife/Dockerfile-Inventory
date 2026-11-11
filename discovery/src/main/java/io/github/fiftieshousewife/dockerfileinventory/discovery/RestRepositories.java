package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;

final class RestRepositories {

    private RestRepositories() {
    }

    static String path(String restPrefix, String organization, int page) {
        return restPrefix + "orgs/%s/repos?per_page=100&type=all&page=%d".formatted(organization, page);
    }

    static String branchPath(String restPrefix, String repository, String branch) {
        return restPrefix + "repos/%s/branches/%s".formatted(repository, segments(branch));
    }

    static String segments(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("%2F", "/").replace("+", "%20");
    }

    static String headCommit(JsonNode branch) {
        return branch.path("commit").path("sha").stringValue(null);
    }

    static Repository repository(JsonNode node) {
        return new Repository(node.path("full_name").asString(), node.path("default_branch").stringValue(null), null,
                node.path("archived").asBoolean(), node.path("fork").asBoolean(),
                node.path("pushed_at").stringValue(null));
    }
}
