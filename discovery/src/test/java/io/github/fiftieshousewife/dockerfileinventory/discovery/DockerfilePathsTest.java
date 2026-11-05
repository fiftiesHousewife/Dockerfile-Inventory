package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DockerfilePathsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "Dockerfile", "docker/Dockerfile", "Dockerfile.prod", "app.Dockerfile",
        "Containerfile", "build/containerfile", "svc/dockerfile.ubi9"})
    void acceptsDockerfileNames(String path) {
        assertThat(DockerfilePaths.isDockerfile(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Dockerfile/README.md", ".dockerignore", "docs/dockerfiles.md", "DockerfileBuilder.java"})
    void rejectsOtherFiles(String path) {
        assertThat(DockerfilePaths.isDockerfile(path)).isFalse();
    }
}
