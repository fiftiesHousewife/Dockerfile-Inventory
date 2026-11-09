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
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

final class RecordedReplies {

    private final URI api;
    private final Path out;
    private final HttpClient client;
    private final String bearer;
    private final List<String> index = new ArrayList<>();

    RecordedReplies(URI api, Path out, HttpClient client, String token) {
        this.api = api;
        this.out = out;
        this.client = client;
        this.bearer = "Bearer " + token;
    }

    JsonNode get(String path, String file, String accept) {
        HttpResponse<byte[]> response = send(client, HttpRequest.newBuilder(api.resolve(path))
                .header("Authorization", bearer).header("Accept", accept).GET().build());
        write(file, response.body());
        index.add(indexLine(path, file, response));
        return json(response.body());
    }

    static String indexLine(String path, String file, HttpResponse<?> response) {
        return "/artifactory/" + path + "\t" + file + "\t" + response.statusCode() + "\t"
                + response.headers().firstValue("Content-Type").orElse("") + "\t"
                + response.headers().firstValue("Docker-Content-Digest").orElse("") + "\n";
    }

    static JsonNode json(byte[] body) {
        try {
            return Json.MAPPER.readTree(body);
        } catch (RuntimeException notJson) {
            return Json.MAPPER.createObjectNode();
        }
    }

    int writeIndex() {
        write("index.tsv", String.join("", index).getBytes(StandardCharsets.UTF_8));
        return index.size();
    }

    void clear() {
        try (Stream<Path> files = Files.isDirectory(out) ? Files.list(out) : Stream.empty()) {
            files.filter(file -> file.toString().endsWith(".json") || file.endsWith("index.tsv")).toList()
                    .forEach(RecordedReplies::delete);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    static HttpResponse<byte[]> send(HttpClient client, HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
