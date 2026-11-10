package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class ImageReferenceTest {

    @Test
    void aReferenceLosesItsRegistryHostAndKeepsItsPathTagAndDigest() {
        assertAll(
                () -> assertThat(ImageReference.of("quay.io/openshift/origin-cli:4.18"))
                        .isEqualTo(new ImageReference("openshift/origin-cli", "4.18", null)),
                () -> assertThat(ImageReference.of("registry.local:5000/team/app:1.2@sha256:ab"))
                        .isEqualTo(new ImageReference("team/app", "1.2", "sha256:ab")),
                () -> assertThat(ImageReference.of("localhost/app"))
                        .isEqualTo(new ImageReference("app", null, null)),
                () -> assertThat(ImageReference.of("team/app@sha256:ab"))
                        .isEqualTo(new ImageReference("team/app", null, "sha256:ab")));
    }

    @Test
    void aReferenceFloatsWithLatestOrNoTagUnlessADigestPinsIt() {
        assertAll(
                () -> assertThat(ImageReference.of("rhel7").floating()).isTrue(),
                () -> assertThat(ImageReference.of("rhel7:latest").floating()).isTrue(),
                () -> assertThat(ImageReference.of("rhel7:7.9").floating()).isFalse(),
                () -> assertThat(ImageReference.of("rhel7:latest@sha256:ab").floating()).isFalse());
    }

    @Test
    void aFirstSegmentIsAHostWithADotAPortOrAsLocalhost() {
        assertAll(
                () -> assertThat(ImageReference.host("quay.io")).isTrue(),
                () -> assertThat(ImageReference.host("registry:5000")).isTrue(),
                () -> assertThat(ImageReference.host("localhost")).isTrue(),
                () -> assertThat(ImageReference.host("openshift")).isFalse());
    }

    @Test
    void aReferenceWithoutATagMeansLatest() {
        assertThat(ImageReference.of("rhel7").tagOrLatest()).isEqualTo("latest");
    }
}
