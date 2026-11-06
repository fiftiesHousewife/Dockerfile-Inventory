package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DockerfilePathsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "Dockerfile", "docker/Dockerfile", "Dockerfile.prod", "app.Dockerfile",
        "Containerfile", "build/containerfile", "svc/dockerfile.ubi9", "3.11/Dockerfile.rhel9",
        "3.12/Dockerfile.c10s", "test/images/Dockerfile"})
    void acceptsDockerfileNames(String path) {
        assertThat(DockerfilePaths.isDockerfile(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Dockerfile/README.md", ".dockerignore", "docs/dockerfiles.md", "DockerfileBuilder.java"})
    void rejectsOtherFiles(String path) {
        assertThat(DockerfilePaths.isDockerfile(path)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "test/extended/builds/dockerfile.go", "contrib/Docker.tmbundle/Dockerfile.tmPreferences",
        "Dockerfile.tpl", "src/Dockerfile.template", "Dockerfile.j2", "Dockerfile.jinja", "README.Dockerfile.md",
        "Dockerfile.erb", "Dockerfile.in", "schema/Dockerfile.json", "Dockerfile.yaml", "Dockerfile.txt"})
    void rejectsSourceDocumentAndTemplateFilesNamedLikeDockerfiles(String path) {
        assertThat(DockerfilePaths.isDockerfile(path)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "vendor/k8s.io/kubernetes/test/images/redis/Dockerfile", "web/node_modules/pkg/Dockerfile",
        "third_party/tool/Dockerfile", "a/b/vendor/c/Dockerfile"})
    void rejectsThirdPartyCopies(String path) {
        assertThat(DockerfilePaths.isDockerfile(path)).isFalse();
    }
}
