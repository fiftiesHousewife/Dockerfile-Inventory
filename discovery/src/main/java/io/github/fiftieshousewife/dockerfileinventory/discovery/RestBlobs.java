package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.IntStream;
import tools.jackson.databind.JsonNode;

final class RestBlobs {

    // Git calls a file binary when a NUL is in its first 8000 bytes, as GraphQL's isBinary does.
    private static final int SNIFFED = 8000;

    private RestBlobs() {
    }

    static String path(String restPrefix, BlobRequest request) {
        return restPrefix + "repos/%s/git/blobs/%s".formatted(request.repository(), request.sha());
    }

    static BlobText blob(String sha, JsonNode node) {
        byte[] bytes = Base64.getMimeDecoder().decode(node.path("content").asString());
        return binary(bytes) ? new BlobText(sha, null, false, true)
                : new BlobText(sha, new String(bytes, StandardCharsets.UTF_8), false, false);
    }

    static boolean binary(byte[] bytes) {
        return IntStream.range(0, Math.min(bytes.length, SNIFFED)).anyMatch(i -> bytes[i] == 0);
    }
}
