package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Records a local JCR's replies to every request lineage's ArtifactoryHttp makes, as test fixtures: the repository
 * list, each repository's catalogue, each image's tags, and for each tag the manifest, the config blob and the storage
 * API's file info, byte for byte, with {@code index.tsv} listing per request its path, file, status, Content-Type and
 * Docker-Content-Digest. The token is made here, used and never written. {@code recordJcrFixtures}; settings
 * {@code JCR_PORT} (8082), {@code ADMIN_PASSWORD} ({@code password}), {@code OUT}
 * ({@code lineage/src/test/resources/fixtures/artifactory-jcr}).
 */
public final class JcrRecord {

    static final int PAGE = 1000;
    static final String MANIFESTS = String.join(", ", "application/vnd.docker.distribution.manifest.v2+json",
            "application/vnd.docker.distribution.manifest.list.v2+json", "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.oci.image.index.v1+json");

    private final URI api;
    private final String password;
    private final Path out;
    private final HttpClient client;

    JcrRecord(URI api, String password, Path out, HttpClient client) {
        this.api = api;
        this.password = password;
        this.out = out;
        this.client = client;
    }

    /**
     * Records from the JCR on {@code JCR_PORT} into {@code OUT}.
     *
     * @param args none
     */
    public static void main(String[] args) {
        of(System.getenv()).run();
    }

    static JcrRecord of(Map<String, String> env) {
        return new JcrRecord(URI.create("http://localhost:" + env.getOrDefault("JCR_PORT", "8082") + "/artifactory/"),
                env.getOrDefault("ADMIN_PASSWORD", "password"), Path.of(env.getOrDefault("OUT",
                "lineage/src/test/resources/fixtures/artifactory-jcr")), HttpClient.newHttpClient());
    }

    void run() {
        ping();
        RecordedReplies replies = new RecordedReplies(api, out, client, token());
        replies.clear();
        replies.get("api/repositories?type=local&packageType=docker", "repositories.json", "application/json")
                .forEach(repository -> repository(replies, repository.path("key").asString()));
        System.out.println("recorded " + replies.writeIndex() + " replies into " + out);
    }

    private void repository(RecordedReplies replies, String repository) {
        replies.get("api/docker/" + repository + "/v2/_catalog?n=" + PAGE, "catalog-" + repository + ".json",
                "application/json").path("repositories").valueStream().map(JsonNode::asString)
                .forEach(image -> image(replies, repository, image));
    }

    private void image(RecordedReplies replies, String repository, String image) {
        replies.get("api/docker/" + repository + "/v2/" + image + "/tags/list?n=" + PAGE, "tags-" + slug(image)
                + ".json", "application/json").path("tags").valueStream().map(JsonNode::asString)
                .forEach(tag -> tag(replies, repository, image, tag));
    }

    void tag(RecordedReplies replies, String repository, String image, String tag) {
        String docker = "api/docker/" + repository + "/v2/" + image;
        String name = slug(image + ":" + tag);
        String config = replies.get(docker + "/manifests/" + tag, "manifest-" + name + ".json", MANIFESTS)
                .path("config").path("digest").asString("");
        if (!config.isEmpty()) {
            replies.get(docker + "/blobs/" + config, "config-" + name + ".json", "*/*");
        }
        replies.get("api/storage/" + repository + "/" + image + "/" + tag, "storage-" + name + ".json",
                "application/json");
    }

    void ping() {
        try {
            String reply = new String(RecordedReplies.send(client, HttpRequest.newBuilder(api.resolve(
                    "api/system/ping")).build()).body(), StandardCharsets.UTF_8);
            if (!"OK".equals(reply)) {
                throw new IllegalStateException("JCR is not answering at " + api);
            }
        } catch (UncheckedIOException e) {
            throw new IllegalStateException("JCR is not answering at " + api + ": " + e.getMessage(), e);
        }
    }

    String token() {
        String basic = Base64.getEncoder().encodeToString(("admin:" + password).getBytes(StandardCharsets.UTF_8));
        JsonNode reply = Json.MAPPER.readTree(RecordedReplies.send(client, HttpRequest.newBuilder(api.resolve(
                "api/security/token")).header("Authorization", "Basic " + basic).header("Content-Type",
                "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(
                "username=admin&scope=member-of-groups:readers&expires_in=3600")).build()).body());
        String made = reply.path("access_token").asString("");
        if (made.isEmpty()) {
            throw new IllegalStateException("no token from JCR");
        }
        return made;
    }

    static String slug(String name) {
        return name.replace('/', '_').replace(':', '_');
    }
}
