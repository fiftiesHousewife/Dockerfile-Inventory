package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;

final class RestHistory {

    private RestHistory() {
    }

    static String path(String restPrefix, ChangeRequest request) {
        return restPrefix + "repos/%s/commits?sha=%s&path=%s&per_page=1".formatted(request.repository(),
                request.commitSha(), URLEncoder.encode(request.path(), StandardCharsets.UTF_8).replace("+", "%20"));
    }

    static LastChange lastChange(ChangeRequest request, JsonNode page) {
        JsonNode newest = page.path(0);
        return new LastChange(request.repository(), request.commitSha(), request.path(),
                newest.path("sha").stringValue(null), newest.path("commit").path("committer").path("date")
                .stringValue(null));
    }
}
