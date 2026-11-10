package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HttpLogTest {

    private static final HttpRequest REQUEST = HttpRequest.newBuilder(
                    URI.create("https://ghe.example.com/api/v3/repos/acme/api/git/trees/abc?recursive=1&token=x"))
            .header("Authorization", "Bearer secret").GET().build();

    @Test
    void aCallIsItsMethodHostPathStatusAndTimeWithoutItsQueryOrHeaders() {
        assertThat(HttpLog.call(REQUEST, 200, Duration.ofMillis(1234)))
                .isEqualTo("GET ghe.example.com/api/v3/repos/acme/api/git/trees/abc 200 in 1234 ms");
    }

    @Test
    void aFailedCallNamesTheErrorsKind() {
        assertThat(HttpLog.failed(REQUEST, new ConnectException("Connection refused")))
                .isEqualTo("GET ghe.example.com/api/v3/repos/acme/api/git/trees/abc failed: ConnectException");
    }

    @Test
    void aWaitSaysHowLongAndBeforeWhat() {
        assertThat(HttpLog.waiting(REQUEST, Duration.ofMillis(1500)))
                .isEqualTo("waiting 1.5 s before GET ghe.example.com/api/v3/repos/acme/api/git/trees/abc");
    }
}
