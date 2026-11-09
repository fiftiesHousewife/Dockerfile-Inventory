package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OkdChoiceTest {

    private static final String RELEASE = "5.0.0-okd-scos.5";

    private static OkdChoice.Image image(String component, String repository) {
        return new OkdChoice.Image(RELEASE + "-" + component, repository, repository.isEmpty() ? "" : "c-" + component);
    }

    @Test
    void toolsComeFirstThenOneImagePerScannedRepositorySpreadEvenlyAcrossTheRelease() {
        List<OkdChoice.Image> images = List.of(image("a", "openshift/a"), image("a2", "openshift/a"),
                image("b", "openshift/b"), image("c", "openshift/c"), image("d", "openshift/unscanned"),
                image("e", "openshift/e"), image("network-tools", "openshift/nt"), image("tools", "openshift/oc"));

        assertThat(OkdChoice.candidates(RELEASE, images, Set.of("openshift/a", "openshift/b", "openshift/c",
                "openshift/e"), 2)).extracting(OkdChoice.Image::tag).containsExactly(RELEASE + "-network-tools",
                RELEASE + "-tools", RELEASE + "-a", RELEASE + "-c");
    }

    @Test
    void aSpreadTakesEveryImageWhenThereAreNoMoreThanTheCountElseEvenSteps() {
        List<OkdChoice.Image> images = List.of(image("a", "r/a"), image("b", "r/b"), image("c", "r/c"),
                image("d", "r/d"));

        assertAll(
                () -> assertThat(OkdChoice.spread(images, 5)).isEqualTo(images),
                () -> assertThat(OkdChoice.spread(images, 2)).extracting(OkdChoice.Image::tag)
                        .containsExactly(RELEASE + "-a", RELEASE + "-c"));
    }

    @Test
    void imagesAreChosenInOrderWhileThemAndEveryBaseTheyNeedStayWithinTheCount() {
        List<OkdChoice.Image> candidates = List.of(image("tools", "openshift/oc"), image("a", "openshift/a"),
                image("b", "openshift/b"));
        Map<String, List<String>> bases = Map.of(RELEASE + "-tools", List.of("quay.io/okd/base@sha256:1"),
                RELEASE + "-a", List.of("quay.io/okd/base@sha256:1"), RELEASE + "-b", List.of("quay.io/other:9"));

        OkdChoice choice = OkdChoice.of(candidates, bases, 3);

        assertAll(
                () -> assertThat(choice.chosen()).extracting(OkdChoice.Image::tag)
                        .containsExactly(RELEASE + "-tools", RELEASE + "-a"),
                () -> assertThat(choice.bases()).containsExactly("quay.io/okd/base@sha256:1"));
    }

    @Test
    void theMirrorListsTheBasesThenTheChosenImagesWithTheirNamesInJcr() {
        OkdChoice choice = new OkdChoice(List.of(), List.of(image("tools", "openshift/oc")),
                List.of("quay.io/okd/scos-content@sha256:" + "ab".repeat(32), "quay.io/centos/centos:stream9"));

        assertThat(choice.mirror(RELEASE)).containsExactly(
                "quay.io/okd/scos-content@sha256:" + "ab".repeat(32) + " okd/scos-content:sha256-abababababab",
                "quay.io/centos/centos:stream9 centos/centos:stream9",
                "quay.io/okd/scos-content:" + RELEASE + "-tools okd/tools:" + RELEASE);
    }

    @Test
    void theReleaseIndexIsEachChosenRepositoryAndCommitOnce() {
        OkdChoice choice = new OkdChoice(List.of(), List.of(image("b", "openshift/b"), image("a", "openshift/a"),
                image("x", "")), List.of());

        assertThat(choice.releases()).containsExactly("openshift/a,c-a", "openshift/b,c-b");
    }
}
