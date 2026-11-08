package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.reader.DockerfileReader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class LocalSourcesTest {

    @Test
    void theSourcesTakenFromTheContextAreThoseOfCopyAndAddNotOfStagesUrlsOrHeredocs() {
        String dockerfile = """
                FROM golang AS build
                COPY go.mod go.sum ./
                FROM ubi9
                COPY --from=build /out/app /usr/local/bin/
                COPY bin/app-${TARGETOS}-${TARGETARCH} target/lib/* /opt/
                ADD https://example.com/a.tgz rootfs.tar.gz /
                COPY <<EOF /etc/a
                x
                EOF
                """;

        assertThat(LocalSources.of(DockerfileReader.read(Parsed.of(dockerfile)).model())).containsExactly("go.mod", "go.sum",
                "bin/app-${TARGETOS}-${TARGETARCH}", "target/lib/*", "rootfs.tar.gz");
    }

    @Test
    void aSourceIsRemoteWhenItIsAUrlOrAGitAddress() {
        assertAll(
                () -> assertThat(LocalSources.remote("https://example.com/a.tgz")).isTrue(),
                () -> assertThat(LocalSources.remote("git@github.com:o/r.git")).isTrue(),
                () -> assertThat(LocalSources.remote("rootfs.tar.gz")).isFalse());
    }

    @Test
    void theSecretsADockerfileMountsAreTheIdsOfItsSecretMounts() {
        String dockerfile = """
                FROM ubi9
                RUN --mount=type=secret,id=downloads_url,env=URL --mount=type=cache,target=/c make
                RUN --mount=type=secret,id=token make install
                """;

        assertThat(LocalSources.secretIds(DockerfileReader.read(Parsed.of(dockerfile)).model()))
                .containsExactly("downloads_url", "token");
    }
}
