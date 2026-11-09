package io.github.fiftieshousewife.dockerfileinventory.tools;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

// quay.io read anonymously: a pull token per repository, the tag API's pages, manifests and the config blob.
final class QuayHttp implements Quay {

    private static final String ACCEPT = String.join(",", "application/vnd.oci.image.index.v1+json",
            "application/vnd.docker.distribution.manifest.list.v2+json", "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.docker.distribution.manifest.v2+json");

    private final URI base;
    private final HttpClient client;

    QuayHttp(URI base, HttpClient client) {
        this.base = base;
        this.client = client;
    }

    QuayHttp(Map<String, String> env) {
        this(URI.create("https://quay.io"), HttpSettings.from(env).client());
    }

    @Override
    public List<String> tags(String release) {
        List<String> tags = new ArrayList<>();
        JsonNode page;
        int number = 1;
        do {
            page = json(get("/api/v1/repository/" + OkdChoice.CONTENT + "/tag/?limit=100&page=" + number
                    + "&onlyActiveTags=true&filter_tag_name=" + URLEncoder.encode("like:" + release + "-",
                    StandardCharsets.UTF_8), null, null));
            page.path("tags").forEach(tag -> tags.add(tag.path("name").asString()));
            number++;
        } while (page.path("has_additional").asBoolean(false));
        return tags;
    }

    // The amd64 image's config, through a manifest list when there is one.
    @Override
    public String config(String tag) {
        String bearer = "Bearer " + json(get("/v2/auth?service=quay.io&scope=repository:" + OkdChoice.CONTENT
                + ":pull", null, null)).path("token").asString();
        JsonNode manifest = json(get("/v2/" + OkdChoice.CONTENT + "/manifests/" + tag, bearer, ACCEPT));
        for (JsonNode entry : manifest.path("manifests")) {
            if (entry.path("platform").path("architecture").asString().equals("amd64")) {
                manifest = json(get("/v2/" + OkdChoice.CONTENT + "/manifests/" + entry.path("digest").asString(),
                        bearer, ACCEPT));
                break;
            }
        }
        return get("/v2/" + OkdChoice.CONTENT + "/blobs/" + manifest.path("config").path("digest").asString(),
                bearer, null);
    }

    private String get(String path, String authorization, String accept) {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofMinutes(2));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        if (accept != null) {
            request.header("Accept", accept);
        }
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("GET " + base.resolve(path) + " returned " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode json(String body) {
        return Json.MAPPER.readTree(body);
    }
}
