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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Artifactory over REST. {@code base} is the {@code /artifactory/} URL; the Docker v2 API for a repository lives
 * under {@code api/docker/<repo>/v2/}. Only {@code java.net.http} is used.
 */
public final class ArtifactoryHttp implements Artifactory {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int PAGE_SIZE = 1000;

    private final HttpClient http;
    private final URI base;
    private final String token;
    private final int pageSize;
    private final ObjectMapper json = new ObjectMapper();

    public ArtifactoryHttp(URI base, String token) {
        this(base, token, PAGE_SIZE);
    }

    ArtifactoryHttp(URI base, String token, int pageSize) {
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
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

    private JsonNode getJson(String path) {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        try {
            return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
        } catch (IOException e) {
            throw new ArtifactoryException("GET " + request.uri() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactoryException("interrupted calling " + request.uri(), e);
        }
    }
}
