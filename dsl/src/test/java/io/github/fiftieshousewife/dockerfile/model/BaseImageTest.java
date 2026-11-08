package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class BaseImageTest {

    @Test
    void aDigestFromABuildArgumentIsModelledButAPlaceholderIsNot() {
        assertThat(new BaseImage("quay.io/prometheus/busybox", null, "sha256:${BASE_DOCKER_SHA}").digest())
                .isEqualTo("sha256:${BASE_DOCKER_SHA}");
        assertThat(new BaseImage("busybox", null, "sha256:$SHA").digest()).isEqualTo("sha256:$SHA");
        assertThatIllegalArgumentException().isThrownBy(() -> new BaseImage("busybox", null, "sha256..."));
    }

    @Test
    void aDigestIsSixtyFourHexCharactersAndANameIsRequired() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BaseImage("ubi9/ubi-minimal", "9.4", "sha256:abc"))
                .withMessageContaining("digest");
        assertThatIllegalArgumentException().isThrownBy(() -> new BaseImage(" ", "9.4", null));
        assertThat(new BaseImage("ubi9/ubi-minimal", "9.4", "sha256:" + "a".repeat(64)).digest()).hasSize(71);
    }

    @Test
    void theFamilyIsTheLastPathSegment() {
        assertThat(new BaseImage("registry.example.com/ubi9/ubi-minimal", "9.4", null).family())
                .isEqualTo("ubi-minimal");
        assertThat(new BaseImage("alpine", "3.20", null).family()).isEqualTo("alpine");
    }
}
