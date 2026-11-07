package io.github.fiftieshousewife.dockerfileinventory.discovery;

import tools.jackson.databind.JsonNode;
import java.util.Optional;

final class GraphQlRepositories {

    static final String QUERY = """
            query($org: String!, $cursor: String) {
              organization(login: $org) {
                repositories(first: 100, after: $cursor) {
                  pageInfo { hasNextPage endCursor }
                  nodes {
                    nameWithOwner isArchived isFork pushedAt
                    defaultBranchRef { name target { oid } }
                  }
                }
              }
            }""";

    private GraphQlRepositories() {
    }

    static Optional<String> nextCursor(JsonNode page) {
        JsonNode pageInfo = page.path("pageInfo");
        return pageInfo.path("hasNextPage").asBoolean()
                ? Optional.of(pageInfo.path("endCursor").asString())
                : Optional.empty();
    }

    static Repository repository(JsonNode node) {
        JsonNode branch = node.path("defaultBranchRef");
        return new Repository(
                node.path("nameWithOwner").asString(),
                branch.path("name").stringValue(null),
                branch.path("target").path("oid").stringValue(null),
                node.path("isArchived").asBoolean(),
                node.path("isFork").asBoolean(),
                node.path("pushedAt").stringValue(null));
    }
}
