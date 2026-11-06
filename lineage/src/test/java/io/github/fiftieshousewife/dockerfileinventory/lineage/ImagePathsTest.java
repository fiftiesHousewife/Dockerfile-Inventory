package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImagePathsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "registry.access.redhat.com/ubi9/s2i-base:9.8-1779892026 | ubi9/s2i-base",
        "registry.example.com:5000/team/app:1.0@sha256:abc        | team/app",
        "localhost/team/app                                       | team/app",
        "artifactory:8443/team/app                                | team/app",
        "registry.example.com:5000/team/app                       | team/app",
        "docker-local/team/app:1                                  | docker-local/team/app",
        "ubi9:9.8                                                 | ubi9",
    })
    void dropsTheRegistryHostTagAndDigest(String reference, String path) {
        assertThat(ImagePaths.path(reference)).isEqualTo(path);
    }
}
