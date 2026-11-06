package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Artifactory over REST. {@code base} is the {@code /artifactory/} URL; the Docker v2 API for a repository lives
 * under {@code api/docker/<repo>/v2/}. Only {@code java.net.http} is used.
 */
public final class ArtifactoryHttp implements Artifactory {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int PAGE_SIZE = 1000;

    /** Every manifest and index type, Docker and OCI, so a manifest list comes back as a list and not one platform. */
    private static final String MANIFEST_TYPES = String.join(", ",
            "application/vnd.docker.distribution.manifest.v2+json",
            "application/vnd.docker.distribution.manifest.list.v2+json",
            "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.oci.image.index.v1+json");

    private final HttpClient http;
    private final URI base;
    private final String token;
    private final int pageSize;
    private final ObjectMapper json = new ObjectMapper();

    public ArtifactoryHttp(URI base, String token) {
        this(base, token, PAGE_SIZE);
    }

    ArtifactoryHttp(URI base, String token, int pageSize) {
        // Artifactory can redirect blob downloads to its file store.
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
        this.base = URI.create(base.toString().replaceAll("/+$", "") + "/");
        this.token = token;
        this.pageSize = pageSize;
    }

    @Override
    public List<String> dockerRepositories() {
        List<String> keys = new ArrayList<>();
        getJson("api/repositories?type=local&packageType=docker").forEach(r -> keys.add(r.path("key").asString()));
        return keys;
    }

    @Override
    public List<String> images(String repository) {
        return paged("api/docker/%s/v2/_catalog".formatted(repository), "repositories");
    }

    @Override
    public List<String> tags(String repository, String image) {
        return paged("api/docker/%s/v2/%s/tags/list".formatted(repository, image), "tags");
    }

    @Override
    public Optional<Manifest> manifest(String repository, String image, String reference) {
        return get("api/docker/%s/v2/%s/manifests/%s".formatted(repository, image, reference), MANIFEST_TYPES)
                .map(r -> new Manifest(r.headers().firstValue("Content-Type").orElse(""),
                        r.headers().firstValue("Docker-Content-Digest").orElse(""), r.body()));
    }

    @Override
    public Optional<String> blob(String repository, String image, String digest) {
        return get("api/docker/%s/v2/%s/blobs/%s".formatted(repository, image, digest), "*/*")
                .map(HttpResponse::body);
    }

    /**
     * Docker v2 paging: {@code n} per page, {@code last} the final entry of the previous page. Artifactory does not
     * always send a {@code Link} header, so a page shorter than {@code n} is taken as the end.
     */
    private List<String> paged(String path, String field) {
        List<String> all = new ArrayList<>();
        int pageLength;
        do {
            String last = all.isEmpty() ? "" : "&last=" + URLEncoder.encode(all.getLast(), StandardCharsets.UTF_8);
            JsonNode entries = getJson(path + "?n=" + pageSize + last).path(field);
            entries.forEach(e -> all.add(e.asString()));
            pageLength = entries.size();
        } while (pageLength == pageSize);
        return all;
    }

    /** A JSON GET; a 404 reads as an empty document, so callers see nothing listed. */
    private JsonNode getJson(String path) {
        return get(path, "application/json").map(r -> json.readTree(r.body())).orElseGet(json::createObjectNode);
    }

    /** A GET; empty for 404, which here means the image, tag or blob no longer exists. */
    private Optional<HttpResponse<String>> get(String path, String accept) {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", accept)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        return response.statusCode() == 404 ? Optional.empty() : Optional.of(response);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ArtifactoryException("GET " + request.uri() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactoryException("interrupted calling " + request.uri(), e);
        }
    }
}
