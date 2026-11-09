package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class RegeneratedTest {

    @Test
    void aDockerfileIsRenderedAsTheConverterWritesItWithTheFeaturesItUses() {
        String original = "FROM ubi9\nCOPY <<EOF /etc/a\nx\nEOF\nRUN   microdnf install -y git && microdnf clean all\n";

        assertThat(Regenerated.of(original)).isEqualTo("""
                FROM ubi9
                COPY <<EOF /etc/a
                x
                EOF
                RUN microdnf install -y git \\
                    && microdnf clean all
                """);
    }

    @Test
    void aDockerfileThatDoesNotParseOrConvertIsRefused() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Regenerated.of("not a dockerfile\n")),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Regenerated.of("FROM ubi9@sha256:x\n")));
    }

    @Test
    void theLocalSourcesAreThoseTheContextMustHold() {
        assertThat(Regenerated.localSources("FROM ubi9\nCOPY app.jar /opt/\nCOPY --from=x /a /b\n"))
                .containsExactly("app.jar");
    }

    @Test
    void aDockerfileThatParsesIsItsTreeAndOneThatDoesNotIsRefused() {
        assertAll(() -> assertThat(Regenerated.parsed("FROM ubi9\n").getStages()).hasSize(1),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Regenerated.parsed("not a dockerfile\n"))
                        .withMessage("the Dockerfile does not parse"));
    }
}
