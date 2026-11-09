package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuayHttpTest {

    private HttpServer server;
    private QuayHttp quay;

    @BeforeEach
    void aLoopbackQuay() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/auth", exchange -> reply(exchange, "{\"token\":\"t1\"}"));
        server.createContext("/api/v1/repository/okd/scos-content/tag/", exchange -> reply(exchange,
                exchange.getRequestURI().getQuery().contains("page=1")
                        ? "{\"tags\":[{\"name\":\"r-b\"}],\"has_additional\":true}"
                        : "{\"tags\":[{\"name\":\"r-a\"}],\"has_additional\":false}"));
        server.createContext("/v2/okd/scos-content/manifests/", exchange -> {
            boolean bearer = "Bearer t1".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            String path = exchange.getRequestURI().getPath();
            reply(exchange, !bearer ? "{}" : path.endsWith("/r-a")
                    ? "{\"manifests\":[{\"platform\":{\"architecture\":\"arm64\"},\"digest\":\"sha256:arm\"},"
                    + "{\"platform\":{\"architecture\":\"amd64\"},\"digest\":\"sha256:amd\"}]}"
                    : "{\"config\":{\"digest\":\"sha256:cfg\"}}");
        });
        server.createContext("/v2/okd/scos-content/blobs/sha256:cfg", exchange -> reply(exchange, "{\"config\":{}}"));
        server.start();
        quay = new QuayHttp(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void aReleasesTagsAreReadPageByPage() {
        assertThat(quay.tags("r")).isEqualTo(List.of("r-b", "r-a"));
    }

    @Test
    void aConfigIsTheAmd64ImagesThroughTheManifestList() {
        assertThat(quay.config("r-a")).isEqualTo("{\"config\":{}}");
    }
}
