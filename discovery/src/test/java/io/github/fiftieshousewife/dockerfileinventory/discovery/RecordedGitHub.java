package io.github.fiftieshousewife.dockerfileinventory.discovery;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

final class RecordedGitHub implements AutoCloseable {

    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    final List<String> requests = new CopyOnWriteArrayList<>();
    final List<String> authorizations = new CopyOnWriteArrayList<>();
    final List<String> graphqlBodies = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    RecordedGitHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    URI root() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    void route(String path, Handler handler) {
        server.createContext(path, exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            handler.handle(exchange);
        });
    }

    void routeFixture(String path, String fixture) {
        route(path, exchange -> respond(exchange, 200, fixture));
    }

    void routeGraphql(String fixture) {
        route("/api/graphql", exchange -> {
            body(exchange);
            respond(exchange, 200, fixture);
        });
    }

    String body(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        graphqlBodies.add(body);
        return body;
    }

    static void respond(HttpExchange exchange, int status, String fixture) throws IOException {
        respondText(exchange, status, fixture(fixture));
    }

    static void respondText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static String fixture(String name) {
        try (InputStream in = RecordedGitHub.class.getResourceAsStream("/fixtures/ghe/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
