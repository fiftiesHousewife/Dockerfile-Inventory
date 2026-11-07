package io.github.fiftieshousewife.dockerfileinventory.discovery;

import tools.jackson.databind.JsonNode;
import java.util.stream.StreamSupport;

final class RestTrees {

    private RestTrees() {
    }

    static String path(String restPrefix, String repository, String sha, boolean recursive) {
        return restPrefix + "repos/%s/git/trees/%s%s".formatted(repository, sha, recursive ? "?recursive=1" : "");
    }

    static Tree tree(JsonNode node) {
        var entries = StreamSupport.stream(node.path("tree").spliterator(), false).map(RestTrees::entry).toList();
        return new Tree(node.path("truncated").asBoolean(), entries);
    }

    static Tree.Entry entry(JsonNode e) {
        return new Tree.Entry(e.path("path").asString(), e.path("mode").asString(), e.path("type").asString(),
                e.path("sha").asString());
    }
}
