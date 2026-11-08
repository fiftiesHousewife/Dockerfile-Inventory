package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.RegistryImagesTest.LINEAGE;
import static io.github.fiftieshousewife.dockerfileinventory.report.RegistryImagesTest.PYTHON;
import static io.github.fiftieshousewife.dockerfileinventory.report.RegistryImagesTest.S2I_BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HierarchiesTest {

    private static final String S2I_CORE = "sha256:af64eaa11e298741102f738cc615160bd66b01625c58e7736a8551fe80923b5d";
    private static final String CENTOS = "sha256:cdc65e3dc1b675127b1e26ef79884f63cae4c46c00a89f0c18cb5c617bec62c8";
    private static final String PYTHON_DOCKERFILE = "22461d2436a54f83f08d9410d05897f7562a6c23";

    private static Hierarchies hierarchies(Map<String, RegistryImage> images, int layerLimit) {
        return new Hierarchies(images, 12, layerLimit);
    }

    private static CatalogueEntry withLayers(CatalogueEntry entry, int layers, List<CatalogueEntry.Place> places) {
        CatalogueEntry.Build build = entry.build();
        return new CatalogueEntry(entry.blobSha(), places, false, entry.image(), new CatalogueEntry.Build(
                build.stages(), build.packages(), build.buildKitFeatures(), layers, build.runs(), build.opaqueRuns(),
                build.instructions(), build.runtimes()), null, null, null, null);
    }

    private static CatalogueEntry python() {
        return withLayers(CatalogueLinksTest.entry(PYTHON_DOCKERFILE, "quay.io/sclorg/s2i-base-c9s:c9s", Map.of()),
                1, List.of(new CatalogueEntry.Place("sclorg/s2i-python-container", "3.12/Dockerfile.c9s", "release",
                        "abc")));
    }

    @Test
    void anImagesChainRunsFromItselfToTheRootWithEachStepsOwnLayers() {
        List<Hierarchies.Step> chain = hierarchies(RegistryImages.read(LINEAGE), 40).chain(PYTHON);

        assertAll(
                () -> assertThat(chain).extracting(Hierarchies.Step::configDigest)
                        .containsExactly(PYTHON, S2I_BASE, S2I_CORE, CENTOS),
                () -> assertThat(chain).extracting(Hierarchies.Step::ownLayers).containsExactly(1, 1, 1, 2));
    }

    @Test
    void anImageIsBlockedByAnUnmaintainedStepAboveIt() {
        Map<String, RegistryImage> images = new HashMap<>(RegistryImages.read(LINEAGE));
        RegistryImage base = images.get(S2I_BASE);
        images.put(S2I_BASE, new RegistryImage(base.configDigest(), base.names(), base.parent(), base.blobShas(),
                base.layers(), base.created(), base.lastPushed(), "sha256:0b1c"));

        assertThat(hierarchies(images, 40).built(PYTHON).blockedBy()).extracting(Hierarchies.Step::configDigest)
                .containsExactly(S2I_BASE);
    }

    @Test
    void layersAreCountedFromTheImageWhenTheRegistryHasOne() {
        CatalogueEntry python = python();

        assertThat(hierarchies(RegistryImages.read(LINEAGE), 40).of(python, List.of(python), Map.of(), Set.of())
                .layers()).isEqualTo(new Hierarchies.Layers(1, 4, 5, "image", false));
    }

    @Test
    void withoutAnImageLayersAreDeclaredWithInferredAncestorsAsInherited() {
        CatalogueEntry base = withLayers(CatalogueLinksTest.entry("aaa", "registry.access.redhat.com/ubi9/ubi:9.6",
                Map.of("name", "acme/base")), 3, List.of());
        CatalogueEntry app = withLayers(CatalogueLinksTest.entry("bbb", "registry.example.com/acme/base:1",
                Map.of("name", "acme/app")), 2, List.of());
        List<CatalogueEntry> entries = List.of(base, app);

        assertThat(hierarchies(Map.of(), 40).of(app, entries, CatalogueLinks.of(entries, null), Set.of()).layers())
                .isEqualTo(new Hierarchies.Layers(2, 3, 5, "declared", false));
    }

    @Test
    void anImageOverTheLayerLimitIsFlagged() {
        CatalogueEntry python = python();

        assertThat(hierarchies(RegistryImages.read(LINEAGE), 4).of(python, List.of(python), Map.of(), Set.of())
                .layers().overLimit()).isTrue();
    }

    @Test
    void anEntryCarriesTheImagesBuiltFromItAndWhyItIsUnmaintained() {
        CatalogueEntry python = python();

        Hierarchies.Hierarchy hierarchy = hierarchies(RegistryImages.read(LINEAGE), 40)
                .of(python, List.of(python), Map.of(), Set.of("sclorg/s2i-python-container"));

        assertAll(
                () -> assertThat(hierarchy.images()).extracting(Hierarchies.BuiltImage::configDigest)
                        .containsExactly(PYTHON),
                () -> assertThat(hierarchy.unmaintained()).containsExactly("every repository holding it is archived"));
    }

    @Test
    void theRootInheritsNoLayers() {
        Map<String, RegistryImage> images = RegistryImages.read(LINEAGE);

        assertThat(hierarchies(images, 40).parentLayers(images.get(CENTOS))).isZero();
    }

    @Test
    void aDockerfileNoImageWasBuiltFromHasNoNewestImage() {
        assertThat(hierarchies(RegistryImages.read(LINEAGE), 40).newestImage("aaa")).isEmpty();
    }
}
