package io.github.fiftieshousewife.dockerfileinventory.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

record CannedResponse(int statusCode, String body, HttpHeaders headers) implements HttpResponse<String> {

    static final URI URI = java.net.URI.create("https://server.example/");

    static CannedResponse of(int status, String body) {
        return of(status, body, Map.of());
    }

    static CannedResponse of(int status, String body, Map<String, List<String>> headers) {
        return new CannedResponse(status, body, HttpHeaders.of(headers, (k, v) -> true));
    }

    @Override
    public HttpRequest request() {
        return HttpRequest.newBuilder(URI).GET().build();
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public URI uri() {
        return URI;
    }

    @Override
    public HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1;
    }
}
