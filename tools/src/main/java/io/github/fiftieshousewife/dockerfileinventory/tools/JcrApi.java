package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;

// Tokens are kept in memory and never printed: a refusal shows the reply without them.
final class JcrApi {

    private final URI root;
    private final String admin;
    private final HttpClient client;

    JcrApi(URI root, String password, HttpClient client) {
        this.root = root;
        this.admin = "Basic " + Base64.getEncoder().encodeToString(("admin:" + password)
                .getBytes(StandardCharsets.UTF_8));
        this.client = client;
    }

    boolean answers() {
        try {
            return "OK".equals(send(get("artifactory/api/system/ping").build()).body());
        } catch (UncheckedIOException notYet) {
            return false;
        }
    }

    // JCR's setup wizard names its repository itself, so without the one asked for, its first is taken.
    Optional<String> repository(String wanted) {
        JsonNode list = Json.MAPPER.readTree(send(get("artifactory/api/repositories?type=local&packageType=docker")
                .header("Authorization", admin).build()).body());
        if (!list.isArray() || list.isEmpty()) {
            return Optional.empty();
        }
        return StreamSupport.stream(list.spliterator(), false).map(r -> r.path("key").asString())
                .filter(wanted::equals).findFirst().or(() -> Optional.of(list.get(0).path("key").asString()));
    }

    boolean adminAccepted() {
        return send(get("artifactory/api/repositories").header("Authorization", admin).build()).statusCode() == 200;
    }

    String create(String key) {
        HttpResponse<String> reply = send(HttpRequest.newBuilder(root.resolve("artifactory/api/repositories/" + key))
                .header("Authorization", admin).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"key\":\"" + key + "\",\"rclass\":\"local\","
                        + "\"packageType\":\"docker\",\"dockerApiVersion\":\"V2\"}")).build());
        return reply.body() + " (HTTP " + reply.statusCode() + ")";
    }

    boolean agreementPending(String repository) {
        return send(get("artifactory/api/docker/" + repository + "/v2/").header("Authorization", admin).build())
                .body().contains("accept the EULA");
    }

    void acceptAgreement() {
        send(HttpRequest.newBuilder(root.resolve("artifactory/ui/jcr/eula/accept")).header("Authorization", admin)
                .POST(HttpRequest.BodyPublishers.noBody()).build());
    }

    boolean dockerApiReady(String repository) {
        return send(get("artifactory/api/docker/" + repository + "/v2/token").header("Authorization", admin)
                .build()).statusCode() == 200;
    }

    String token() {
        String reply = send(HttpRequest.newBuilder(root.resolve("access/api/v1/tokens")).header("Authorization", admin)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                        "{\"scope\":\"applied-permissions/admin\",\"expires_in\":86400}")).build()).body();
        String token = accessToken(reply);
        if (!token.isEmpty() && canRead(token)) {
            return token;
        }
        String older = send(HttpRequest.newBuilder(root.resolve("artifactory/api/security/token"))
                .header("Authorization", admin).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=admin&scope=member-of-groups:readers"
                        + "&expires_in=86400")).build()).body();
        token = accessToken(older);
        if (token.isEmpty() || !canRead(token)) {
            throw new IllegalStateException("no usable token from JCR's access API or its older one; create one in "
                    + "the UI (Administration > User Management > Access Tokens) and rerun with ARTIFACTORY_TOKEN set");
        }
        return token;
    }

    boolean canRead(String token) {
        return send(get("artifactory/api/repositories").header("Authorization", "Bearer " + token).build())
                .statusCode() == 200;
    }

    private static String accessToken(String reply) {
        try {
            return Json.MAPPER.readTree(reply).path("access_token").asString("");
        } catch (RuntimeException notJson) {
            return "";
        }
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(root.resolve(path)).GET();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
