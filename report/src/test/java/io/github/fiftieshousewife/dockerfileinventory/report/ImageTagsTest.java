package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ImageTagsTest {

    @Test
    void aTagsNameIsItsImageAndTag() {
        assertThat(ImageTags.name(json("{\"image\":\"ubi9/ubi\",\"tag\":\"9.4\"}"))).isEqualTo("ubi9/ubi:9.4");
    }

    @Test
    void anImagesNamesAreItsTagsWithoutRepeats() {
        assertThat(ImageTags.names(List.of(json("{\"image\":\"a\",\"tag\":\"1\"}"),
                json("{\"image\":\"a\",\"tag\":\"1\"}"), json("{\"image\":\"a\",\"tag\":\"2\"}"))))
                .containsExactly("a:1", "a:2");
    }

    @Test
    void anImagesLastPushIsItsNewestTagsPush() {
        assertThat(ImageTags.lastPushed(List.of(json("{\"lastPushed\":\"2026-01-01T00:00:00Z\"}"),
                json("{\"lastPushed\":\"2026-02-01T00:00:00Z\"}"), json("{}"))))
                .isEqualTo("2026-02-01T00:00:00Z");
    }

    @Test
    void anImageWithACosignSignatureTagBesideItIsSigned() {
        assertThat(ImageTags.provenance(List.of(json("{\"image\":\"a\",\"manifestDigest\":\"sha256:ab\"}")),
                Map.of("a", Set.of("1", "sha256-ab.sig")))).isEqualTo(new Provenance(true, false, false));
    }

    @Test
    void anImageWithoutAManifestDigestHasNoProvenance() {
        assertThat(ImageTags.provenance(List.of(json("{\"image\":\"a\"}")), Map.of("a", Set.of("sha256-ab.sig"))))
                .isEqualTo(new Provenance(false, false, false));
    }

    @Test
    void anImagesSizeIsItsTagsCompressedLayers() {
        assertThat(ImageTags.size(List.of(json("{\"size\":39000000}"), json("{\"size\":39000000}"))))
                .isEqualTo(39_000_000L);
    }

    @Test
    void anImageRecordedWithoutASizeHasNone() {
        assertThat(ImageTags.size(List.of(json("{\"tag\":\"1\"}")))).isNull();
    }

    @Test
    void anIndexsArchitecturesAreTheImages() {
        assertThat(ImageTags.architectures(List.of(json("{\"architectures\":[\"linux/amd64\",\"linux/arm64\"]}")),
                json("{\"os\":\"linux\",\"architecture\":\"amd64\"}"))).containsExactly("linux/amd64", "linux/arm64");
    }

    @Test
    void withoutAnIndexTheArchitectureIsTheConfigs() {
        assertThat(ImageTags.architectures(List.of(json("{\"architectures\":[]}")),
                json("{\"os\":\"linux\",\"architecture\":\"arm64\"}"))).containsExactly("linux/arm64");
    }

    @Test
    void withoutAnIndexOrAConfiguredArchitectureThereIsNone() {
        assertThat(ImageTags.architectures(List.of(json("{}")), json("{}"))).isEmpty();
    }

    @Test
    void anImagesUsageIsItsTagsDownloadsSummedAndItsLatestDownload() {
        assertThat(ImageTags.usage(List.of(
                json("{\"downloads\":40,\"lastDownloaded\":\"2026-11-01T08:00:00Z\"}"),
                json("{\"downloads\":2,\"lastDownloaded\":\"2026-11-05T08:00:00Z\"}"))))
                .isEqualTo(new RegistryImage.Usage(42L, "2026-11-05T08:00:00Z"));
    }

    @Test
    void anImageRecordedWithoutDownloadsHasNoUsage() {
        assertThat(ImageTags.usage(List.of(json("{\"tag\":\"1\"}"))))
                .isEqualTo(new RegistryImage.Usage(null, null));
    }

    private static JsonNode json(String text) {
        return new ObjectMapper().readTree(text);
    }
}
