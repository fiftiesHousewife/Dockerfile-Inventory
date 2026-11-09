package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class ImageCaseTest {

    @Test
    void theSourceRepositoryIsTheOwnerAndNameInTheLabelsUrl() {
        assertAll(
                () -> assertThat(ImageCase.repository("https://github.com/openshift/oc.git/tree/main"))
                        .isEqualTo("openshift/oc.git"),
                () -> assertThat(ImageCase.repository("")).isEmpty());
    }

    @Test
    void aConfigWithoutLabelsHasNone() {
        assertThat(ImageCase.labels(Json.MAPPER.readTree("{\"config\":{\"Labels\":null}}"))).isEmpty();
    }

    @Test
    void theSourceRepositoryComesFromTheFirstSourceLabelSet() {
        assertThat(ImageCase.sourceRepository(Json.MAPPER.readTree("{\"io.openshift.build.source-location\":\"\","
                + "\"org.opencontainers.image.source\":\"https://github.com/acme/app\"}"))).isEqualTo("acme/app");
    }

    @Test
    void anImageWithoutALinkExpectsOnlyItsNameAndRepository() {
        assertThat(ImageCase.expected("tools", "acme/app", null).propertyNames())
                .containsExactly("image", "sourceRepository");
    }

    @Test
    void aTrimmedConfigKeepsNullsForWhatTheImageLacks() {
        assertThat(ImageCase.trimmed(Json.MAPPER.readTree("{}"), Json.MAPPER.createObjectNode()).path("rootfs")
                .isNull()).isTrue();
    }

    @Test
    void aNodeTheConfigLacksIsWrittenAsNull() {
        assertAll(
                () -> assertThat(ImageCase.presentOrNull(Json.MAPPER.createObjectNode().path("x"))).isNull(),
                () -> assertThat(ImageCase.presentOrNull(Json.MAPPER.readTree("1"))).isNotNull());
    }
}
