package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.RegistryImagesTest.LINEAGE;
import static io.github.fiftieshousewife.dockerfileinventory.report.RegistryImagesTest.PYTHON;
import static io.github.fiftieshousewife.dockerfileinventory.report.RegistryImagesTest.S2I_BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageChainsTest {

    private static final String S2I_CORE = "sha256:af64eaa11e298741102f738cc615160bd66b01625c58e7736a8551fe80923b5d";
    private static final String CENTOS = "sha256:cdc65e3dc1b675127b1e26ef79884f63cae4c46c00a89f0c18cb5c617bec62c8";
    private static final Instant NEWEST = Instant.parse("2026-11-06T18:18:06.098Z");

    private static ImageChains chains(Map<String, RegistryImage> images) {
        return new ImageChains(images, NEWEST, 12);
    }

    private static RegistryImage withLayers(RegistryImage image, Integer layers, String parent) {
        return new RegistryImage(image.configDigest(), image.names(), parent, image.blobShas(), layers,
                image.created(), image.lastPushed(), null, image.labels(), image.candidates(), image.size(),
                image.architectures(), image.provenance(), image.usage());
    }

    @Test
    void anImagesChainRunsFromItselfToTheRootWithEachStepsOwnLayers() {
        List<Hierarchies.Step> chain = chains(RegistryImages.read(LINEAGE)).chain(PYTHON);

        assertAll(
                () -> assertThat(chain).extracting(Hierarchies.Step::configDigest)
                        .containsExactly(PYTHON, S2I_BASE, S2I_CORE, CENTOS),
                () -> assertThat(chain).extracting(Hierarchies.Step::ownLayers).containsExactly(1, 1, 1, 2));
    }

    @Test
    void aChainThatLoopsBackStopsAtTheFirstRepeat() {
        Map<String, RegistryImage> images = new HashMap<>(RegistryImages.read(LINEAGE));
        images.put(CENTOS, withLayers(images.get(CENTOS), 2, PYTHON));

        assertThat(chains(images).chain(PYTHON)).hasSize(4);
    }

    @Test
    void anUnknownImageHasAnEmptyChain() {
        assertThat(chains(RegistryImages.read(LINEAGE)).chain("sha256:absent")).isEmpty();
    }

    @Test
    void aStepWhoseImageOrParentLayersAreUnknownHasUnknownOwnLayers() {
        Map<String, RegistryImage> images = new HashMap<>(RegistryImages.read(LINEAGE));
        RegistryImage base = images.get(S2I_BASE);
        images.put(S2I_BASE, withLayers(base, null, base.parent()));

        assertThat(chains(images).chain(PYTHON)).extracting(Hierarchies.Step::ownLayers)
                .containsExactly(null, null, 1, 2);
    }

    @Test
    void theRootHasNoParent() {
        Map<String, RegistryImage> images = RegistryImages.read(LINEAGE);

        assertThat(chains(images).parentOf(images.get(CENTOS))).isNull();
    }

    @Test
    void anImagesParentIsTheImageItNames() {
        Map<String, RegistryImage> images = RegistryImages.read(LINEAGE);

        assertThat(chains(images).parentOf(images.get(PYTHON)).configDigest()).isEqualTo(S2I_BASE);
    }

    @Test
    void theRootInheritsNoLayers() {
        Map<String, RegistryImage> images = RegistryImages.read(LINEAGE);

        assertThat(chains(images).parentLayers(images.get(CENTOS))).isZero();
    }

    @Test
    void anImageWhoseParentIsNotInTheRegistryInheritsUnknownLayers() {
        Map<String, RegistryImage> images = RegistryImages.read(LINEAGE);

        assertThat(chains(images).parentLayers(withLayers(images.get(PYTHON), 5, "sha256:absent"))).isNull();
    }

    @Test
    void aStepCarriesTheImagesOwnLayersAndTimes() {
        Map<String, RegistryImage> images = RegistryImages.read(LINEAGE);

        Hierarchies.Step step = chains(images).step(images.get(PYTHON));

        assertAll(
                () -> assertThat(step.ownLayers()).isEqualTo(1),
                () -> assertThat(step.lastPushed()).isEqualTo("2026-11-06T18:18:06.098Z"),
                () -> assertThat(step.unmaintained()).isEmpty());
    }
}
