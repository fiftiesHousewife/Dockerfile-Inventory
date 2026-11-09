package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

// Lineage's effective users, by the Dockerfile each credited image was built from.
final class EffectiveUsers {

    private EffectiveUsers() {
    }

    static Map<String, EffectiveUser> read(Path lineageDir) {
        Map<String, JsonNode> users = new HashMap<>();
        JsonLines.read(lineageDir.resolve("effective.jsonl")).forEach(row ->
                users.put(row.path("configDigest").asString(), row.path("user")));
        Map<String, EffectiveUser> byDockerfile = new HashMap<>();
        JsonLines.read(lineageDir.resolve("links.jsonl")).stream().filter(row -> row.path("credited").asBoolean(false))
                .forEach(row -> {
                    JsonNode user = users.get(row.path("configDigest").asString());
                    if (user != null) {
                        row.path("blobShas").forEach(sha -> byDockerfile.merge(sha.asString(),
                                new EffectiveUser(user.path("value").stringValue(null), setBy(user.path("setBy"))),
                                EffectiveUser::worse));
                    }
                });
        return byDockerfile;
    }

    static String setBy(JsonNode setBy) {
        return switch (setBy.path("kind").asString()) {
            case "ExternalBase" -> "the base " + setBy.path("path").asString();
            case "Image" -> "the image " + setBy.path("configDigest").asString();
            default -> null;
        };
    }
}
