package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetryTest {

    @Test
    void waitsAsLongAsRetryAfterSaysOnA429OrA503() {
        assertThat(Retry.waitBeforeRetry(429, headers(Map.of("Retry-After", List.of("7")))))
                .contains(Duration.ofSeconds(7));
        assertThat(Retry.waitBeforeRetry(503, headers(Map.of("Retry-After", List.of("2")))))
                .contains(Duration.ofSeconds(2));
    }

    @Test
    void waitsTheDefaultWhenRetryAfterIsAbsent() {
        assertThat(Retry.waitBeforeRetry(503, headers(Map.of()))).contains(Retry.DEFAULT_WAIT);
    }

    @Test
    void doesNotRetryAnyOtherStatus() {
        assertThat(Retry.waitBeforeRetry(200, headers(Map.of()))).isEmpty();
        assertThat(Retry.waitBeforeRetry(403, headers(Map.of("Retry-After", List.of("7"))))).isEmpty();
        assertThat(Retry.waitBeforeRetry(500, headers(Map.of()))).isEmpty();
    }

    private static HttpHeaders headers(Map<String, List<String>> values) {
        return HttpHeaders.of(values, (name, value) -> true);
    }
}
