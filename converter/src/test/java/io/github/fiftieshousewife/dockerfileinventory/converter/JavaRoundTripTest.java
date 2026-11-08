package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.model.BaseImage;
import io.github.fiftieshousewife.dockerfile.model.Dockerfile;
import io.github.fiftieshousewife.dockerfile.model.Stage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaRoundTripTest {

    private static final String ORIGINAL = """
            FROM registry.access.redhat.com/ubi9/ubi-minimal:9.4 AS build
            RUN --mount=type=cache,target=/root/.m2 mvn package
            FROM registry.access.redhat.com/ubi9/ubi-minimal:9.4
            COPY --from=build --chmod=755 /out/app /usr/local/bin/app
            HEALTHCHECK --timeout=3s --interval=30s CMD ["app", "check"]
            USER 1001
            ENTRYPOINT ["app"]
            """;

    @Test
    void aGeneratedClassThatRebuildsTheModelPasses() {
        JavaRoundTrip.Candidate candidate = new JavaRoundTrip.Candidate(ORIGINAL, Convert.of(Parsed.of(ORIGINAL)));

        assertThat(JavaRoundTrip.check(Map.of("abc123", candidate))).containsEntry("abc123", new RoundTrip(true, null));
    }

    @Test
    void aBuiltModelThatDiffersFails() {
        JavaRoundTrip.Candidate candidate = new JavaRoundTrip.Candidate(ORIGINAL, Convert.of(Parsed.of(ORIGINAL)));
        Dockerfile other = new Dockerfile(List.of(), List.of(new Stage(null, new BaseImage("alpine", "3", null), null,
                List.of())));

        assertThat(JavaRoundTrip.compare(candidate, other))
                .isEqualTo(new RoundTrip(false, "the generated class builds a different model"));
    }

    @Test
    void aClassThatDoesNotCompileFailsWithItsError() {
        JavaRoundTrip.Candidate candidate = new JavaRoundTrip.Candidate(ORIGINAL, Convert.of(Parsed.of(ORIGINAL)));
        InMemoryCompiler.Compiled compiled = new InMemoryCompiler.Compiled(Map.of(),
                Map.of("roundtrip.Dabc", "code too large"));

        assertThat(JavaRoundTrip.outcome("abc", candidate, compiled, getClass().getClassLoader()))
                .isEqualTo(new RoundTrip(false, "the generated class does not compile: code too large"));
    }
}
