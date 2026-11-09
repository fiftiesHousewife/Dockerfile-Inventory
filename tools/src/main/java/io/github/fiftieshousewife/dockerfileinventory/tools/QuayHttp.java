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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

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

    record Page(int number, JsonNode body) {

        boolean more() {
            return body.path("has_additional").asBoolean(false);
        }
    }

    @Override
    public List<String> tags(String release) {
        return Stream.iterate(page(release, 1), Objects::nonNull, page -> page.more() ? page(release, page.number() + 1)
                : null).flatMap(page -> page.body().path("tags").valueStream()).map(tag -> tag.path("name").asString())
                .toList();
    }

    Page page(String release, int number) {
        return new Page(number, json(get("/api/v1/repository/" + OkdChoice.CONTENT + "/tag/?limit=100&page=" + number
                + "&onlyActiveTags=true&filter_tag_name=" + URLEncoder.encode("like:" + release + "-",
                StandardCharsets.UTF_8), null, null)));
    }

    @Override
    public String config(String tag) {
        String bearer = "Bearer " + json(get("/v2/auth?service=quay.io&scope=repository:" + OkdChoice.CONTENT
                + ":pull", null, null)).path("token").asString();
        JsonNode listed = manifest(tag, bearer);
        JsonNode manifest = listed.path("manifests").valueStream()
                .filter(entry -> entry.path("platform").path("architecture").asString().equals("amd64"))
                .findFirst().map(entry -> manifest(entry.path("digest").asString(), bearer)).orElse(listed);
        return get("/v2/" + OkdChoice.CONTENT + "/blobs/" + manifest.path("config").path("digest").asString(),
                bearer, null);
    }

    private JsonNode manifest(String reference, String bearer) {
        return json(get("/v2/" + OkdChoice.CONTENT + "/manifests/" + reference, bearer, ACCEPT));
    }

    String get(String path, String authorization, String accept) {
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
