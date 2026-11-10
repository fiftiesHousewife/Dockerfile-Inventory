package io.github.fiftieshousewife.dockerfileinventory.discovery;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.stream.Collectors;

final class GraphQlBlobs {

    private static final String FIELD = "  %s: repository(owner: %s, name: %s) "
            + "{ object(oid: \"%s\") { ... on Blob { text isTruncated isBinary } } }";

    private GraphQlBlobs() {
    }

    static String query(List<BlobRequest> requests) {
        return requests.stream()
                .distinct()
                .map(r -> FIELD.formatted(alias(r), quoted(owner(r.repository())), quoted(name(r.repository())),
                        r.sha()))
                .collect(Collectors.joining("\n", "query {\n", "\n}"));
    }

    static List<BlobText> read(JsonNode data, List<BlobRequest> requests) {
        return requests.stream()
                .filter(request -> data.path(alias(request)).path("object").isObject())
                .map(request -> blob(request.sha(), data.path(alias(request)).path("object")))
                .toList();
    }

    static BlobText blob(String sha, JsonNode object) {
        return new BlobText(sha, object.path("text").stringValue(null),
                object.path("isTruncated").asBoolean(), object.path("isBinary").asBoolean());
    }

    static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String owner(String repository) {
        return repository.substring(0, repository.indexOf('/'));
    }

    static String name(String repository) {
        return repository.substring(repository.indexOf('/') + 1);
    }

    private static String alias(BlobRequest request) {
        return "b" + request.sha();
    }
}
