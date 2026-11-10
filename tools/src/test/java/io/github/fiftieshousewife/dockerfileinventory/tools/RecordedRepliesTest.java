package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordedRepliesTest {

    @TempDir
    Path out;

    private HttpServer server;
    private RecordedReplies replies;

    @BeforeEach
    void aLoopbackJcr() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/artifactory/", exchange -> {
            byte[] body = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.getResponseHeaders().add("Docker-Content-Digest", "sha256:d1");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        replies = new RecordedReplies(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/artifactory/"), out, HttpClient.newHttpClient(), "t1");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aReplyIsKeptByteForByteAndIndexedWithItsStatusTypeAndDigest() throws IOException {
        replies.get("api/x?n=1", "x.json", "application/json");
        replies.writeIndex();

        assertAll(
                () -> assertThat(out.resolve("x.json")).hasContent("Bearer t1"),
                () -> assertThat(out.resolve("index.tsv"))
                        .hasContent("/artifactory/api/x?n=1\tx.json\t200\ttext/plain\tsha256:d1"));
    }

    @Test
    void aReplyThatIsNotJsonReadsAsAnEmptyObject() {
        assertThat(RecordedReplies.json("not json".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void clearingRemovesTheLastRecordingButKeepsTheReadme() throws IOException {
        Files.writeString(out.resolve("old.json"), "{}");
        Files.writeString(out.resolve("index.tsv"), "");
        Files.writeString(out.resolve("README.md"), "kept");

        replies.clear();

        try (Stream<Path> files = Files.list(out)) {
            assertThat(files.map(file -> String.valueOf(file.getFileName()))).containsExactly("README.md");
        }
    }

    @Test
    void aRecordingThatCannotBeRemovedIsAnInputOutputFailure() throws IOException {
        Files.createDirectories(out.resolve("full.json"));
        Files.writeString(out.resolve("full.json/inside"), "");

        assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(replies::clear);
    }

    @Test
    void aReplyThatCannotBeWrittenIsAnInputOutputFailure() throws IOException {
        Path blocked = Files.writeString(out.resolve("blocked"), "");
        RecordedReplies into = new RecordedReplies(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/artifactory/"), blocked, HttpClient.newHttpClient(), "t1");

        assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(into::writeIndex);
    }

    @Test
    void aServerThatDoesNotAnswerIsAnInputOutputFailure() {
        assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> RecordedReplies.send(
                HttpClient.newHttpClient(), HttpRequest.newBuilder(URI.create("http://127.0.0.1:1/")).build()));
    }

    @Test
    void clearingAFolderNotYetMadeDoesNothing() {
        new RecordedReplies(URI.create("http://127.0.0.1:1/"), out.resolve("absent"), HttpClient.newHttpClient(),
                "t1").clear();

        assertThat(out.resolve("absent")).doesNotExist();
    }
}
