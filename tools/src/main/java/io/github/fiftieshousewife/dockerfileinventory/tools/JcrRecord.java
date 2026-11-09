package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
    private final List<String> index = new ArrayList<>();
    private String token;

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
        Map<String, String> env = System.getenv();
        new JcrRecord(URI.create("http://localhost:" + env.getOrDefault("JCR_PORT", "8082") + "/artifactory/"),
                env.getOrDefault("ADMIN_PASSWORD", "password"), Path.of(env.getOrDefault("OUT",
                "lineage/src/test/resources/fixtures/artifactory-jcr")), HttpClient.newHttpClient()).run();
    }

    void run() {
        token = token();
        clear();
        get("api/repositories?type=local&packageType=docker", "repositories.json", "application/json")
                .forEach(repository -> repository(repository.path("key").asString()));
        write("index.tsv", String.join("", index).getBytes(StandardCharsets.UTF_8));
        System.out.println("recorded " + index.size() + " replies into " + out);
    }

    private void repository(String repository) {
        String docker = "api/docker/" + repository + "/v2/";
        get(docker + "_catalog?n=" + PAGE, "catalog-" + repository + ".json", "application/json")
                .path("repositories").forEach(image -> {
                    String name = image.asString();
                    get(docker + name + "/tags/list?n=" + PAGE, "tags-" + slug(name) + ".json", "application/json")
                            .path("tags").forEach(tag -> tag(repository, name, tag.asString()));
                });
    }

    private void tag(String repository, String image, String tag) {
        String docker = "api/docker/" + repository + "/v2/" + image;
        String name = slug(image + ":" + tag);
        String config = get(docker + "/manifests/" + tag, "manifest-" + name + ".json", MANIFESTS)
                .path("config").path("digest").asString("");
        if (!config.isEmpty()) {
            get(docker + "/blobs/" + config, "config-" + name + ".json", "*/*");
        }
        get("api/storage/" + repository + "/" + image + "/" + tag, "storage-" + name + ".json", "application/json");
    }

    // One request as lineage makes it: the body kept byte for byte, the reply indexed.
    private JsonNode get(String path, String file, String accept) {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(api.resolve(path))
                .header("Authorization", "Bearer " + token).header("Accept", accept).GET().build());
        write(file, response.body());
        index.add("/artifactory/" + path + "\t" + file + "\t" + response.statusCode() + "\t"
                + response.headers().firstValue("Content-Type").orElse("") + "\t"
                + response.headers().firstValue("Docker-Content-Digest").orElse("") + "\n");
        try {
            return Json.MAPPER.readTree(response.body());
        } catch (RuntimeException notJson) {
            return Json.MAPPER.createObjectNode();
        }
    }

    private String token() {
        try {
            if (!"OK".equals(new String(send(HttpRequest.newBuilder(api.resolve("api/system/ping")).build()).body(),
                    StandardCharsets.UTF_8))) {
                throw new IllegalStateException("JCR is not answering at " + api);
            }
        } catch (UncheckedIOException e) {
            throw new IllegalStateException("JCR is not answering at " + api + ": " + e.getMessage(), e);
        }
        String form = "username=admin&scope=member-of-groups:readers&expires_in=3600";
        String basic = Base64.getEncoder().encodeToString(("admin:" + password)
                .getBytes(StandardCharsets.UTF_8));
        JsonNode reply = Json.MAPPER.readTree(send(HttpRequest.newBuilder(api.resolve("api/security/token"))
                .header("Authorization", "Basic " + basic).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build()).body());
        String made = reply.path("access_token").asString("");
        if (made.isEmpty()) {
            throw new IllegalStateException("no token from JCR");
        }
        return made;
    }

    // A rerun starts clean: the fixtures it wrote last time go, the README stays.
    private void clear() {
        try (Stream<Path> files = Files.isDirectory(out) ? Files.list(out) : Stream.empty()) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json") || f.endsWith("index.tsv")).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String slug(String name) {
        return name.replace('/', '_').replace(':', '_');
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void write(String file, byte[] body) {
        try {
            Files.createDirectories(out);
            Files.write(out.resolve(file), body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
