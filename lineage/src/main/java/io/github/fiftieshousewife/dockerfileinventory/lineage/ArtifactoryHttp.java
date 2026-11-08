package io.github.fiftieshousewife.dockerfileinventory.lineage;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.Sleeper;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class ArtifactoryHttp implements Artifactory {

    private static final int PAGE_SIZE = 1000;

    // Every manifest and index type, Docker and OCI: accepting fewer, Artifactory answers a manifest list with one
    // platform's manifest.
    private static final String MANIFEST_TYPES = String.join(", ",
            "application/vnd.docker.distribution.manifest.v2+json",
            "application/vnd.docker.distribution.manifest.list.v2+json",
            "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.oci.image.index.v1+json");

    private final ArtifactoryClient client;
    private final int pageSize;
    private final ObjectMapper json = new ObjectMapper();

    ArtifactoryHttp(URI base, HttpSettings settings, TokenSource token) {
        this(new ArtifactoryClient(settings.client(), base, token, settings.retries(), settings.requestTimeout(),
                Sleeper.REAL), PAGE_SIZE);
    }

    ArtifactoryHttp(ArtifactoryClient client, int pageSize) {
        this.client = client;
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
        return client.get("api/docker/%s/v2/%s/manifests/%s".formatted(repository, image, reference), MANIFEST_TYPES)
                .map(ArtifactoryHttp::manifest);
    }

    @Override
    public Optional<String> blob(String repository, String image, String digest) {
        return client.get("api/docker/%s/v2/%s/blobs/%s".formatted(repository, image, digest), "*/*")
                .map(HttpResponse::body);
    }

    // A tag holds manifest.json, or list.manifest.json for an index; either's statistics are the tag's.
    @Override
    public Optional<DownloadStats> stats(String repository, String image, String tag) {
        return statsOf(repository, image, tag, "manifest.json")
                .or(() -> statsOf(repository, image, tag, "list.manifest.json"));
    }

    private Optional<DownloadStats> statsOf(String repository, String image, String tag, String file) {
        return client.get("api/storage/%s/%s/%s/%s?stats".formatted(repository, image, tag, file), "application/json")
                .map(this::parse)
                .filter(stats -> stats.has("downloadCount"))
                .map(stats -> new DownloadStats(stats.path("downloadCount").asLong(),
                        stats.path("lastDownloaded").asLong() > 0
                                ? Instant.ofEpochMilli(stats.path("lastDownloaded").asLong()) : null));
    }

    @Override
    public Optional<Instant> lastPushed(String repository, String image, String tag) {
        return client.get("api/storage/%s/%s/%s".formatted(repository, image, tag), "application/json")
                .map(r -> parse(r).path("lastModified").stringValue(null))
                .map(t -> OffsetDateTime.parse(t).toInstant());
    }

    static Manifest manifest(HttpResponse<String> response) {
        return new Manifest(response.headers().firstValue("Content-Type").orElse(""),
                response.headers().firstValue("Docker-Content-Digest").orElse(""), response.body());
    }

    // Artifactory does not always send a Link header, so a page shorter than n is taken as the end.
    List<String> paged(String path, String field) {
        List<String> all = new ArrayList<>();
        int pageLength;
        do {
            JsonNode entries = getJson(path + "?n=" + pageSize + after(all)).path(field);
            entries.forEach(e -> all.add(e.asString()));
            pageLength = entries.size();
        } while (pageLength == pageSize);
        return all;
    }

    static String after(List<String> listed) {
        return listed.isEmpty() ? "" : "&last=" + URLEncoder.encode(listed.getLast(), StandardCharsets.UTF_8);
    }

    // A 404 reads as an empty document, so callers see nothing listed.
    JsonNode getJson(String path) {
        return client.get(path, "application/json").map(this::parse).orElseGet(json::createObjectNode);
    }

    JsonNode parse(HttpResponse<String> response) {
        try {
            return json.readTree(response.body());
        } catch (JacksonException e) {
            throw new ArtifactoryException("unreadable JSON from " + response.uri(), e);
        }
    }
}
