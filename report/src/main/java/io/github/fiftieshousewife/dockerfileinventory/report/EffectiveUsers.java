package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

final class EffectiveUsers {

    private EffectiveUsers() {
    }

    static Map<String, UserKind> read(Path lineageDir) {
        Map<String, Instant> pushed = pushed(lineageDir.resolve("images.jsonl"));
        Map<String, String> users = users(lineageDir.resolve("effective.jsonl"));
        Map<String, UserKind> kinds = new HashMap<>();
        newestImages(lineageDir.resolve("links.jsonl"), pushed)
                .forEach((blob, image) -> kinds.put(blob, UserKind.of(users.get(image))));
        return kinds;
    }

    static Map<String, Instant> pushed(Path images) {
        Map<String, Instant> pushed = new HashMap<>();
        JsonLines.read(images).stream()
                .filter(row -> !row.path("configDigest").isNull() && !row.path("lastPushed").isNull())
                .forEach(row -> pushed.merge(row.path("configDigest").asString(),
                        Instant.parse(row.path("lastPushed").asString()), (a, b) -> a.isAfter(b) ? a : b));
        return pushed;
    }

    static Map<String, String> users(Path effective) {
        Map<String, String> users = new HashMap<>();
        JsonLines.read(effective).forEach(row -> users.put(row.path("configDigest").asString(),
                row.path("user").path("value").stringValue(null)));
        return users;
    }

    static Map<String, String> newestImages(Path links, Map<String, Instant> pushed) {
        Map<String, String> newest = new HashMap<>();
        JsonLines.read(links).stream()
                .filter(row -> !row.path("tier").asString().equals("NONE"))
                .forEach(row -> row.path("blobShas").forEach(blob -> newest.merge(blob.asString(),
                        row.path("configDigest").asString(), (a, b) -> newer(a, b, pushed))));
        return newest;
    }

    static String newer(String a, String b, Map<String, Instant> pushed) {
        return pushed.getOrDefault(a, Instant.MIN).isAfter(pushed.getOrDefault(b, Instant.MIN)) ? a : b;
    }
}
