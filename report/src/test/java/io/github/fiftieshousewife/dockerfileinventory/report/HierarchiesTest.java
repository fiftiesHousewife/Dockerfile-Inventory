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
                build.instructions(), build.runtimes()), null, null, null, null, null);
    }

    private static CatalogueEntry python() {
        return withLayers(CatalogueLinksTest.entry(PYTHON_DOCKERFILE, "quay.io/sclorg/s2i-base-c9s:c9s", Map.of()),
                1, List.of(new CatalogueEntry.Place("sclorg/s2i-python-container", "3.12/Dockerfile.c9s", "release",
                        "abc", null, null)));
    }

    @Test
    void anImageIsBlockedByAnUnmaintainedStepAboveIt() {
        Map<String, RegistryImage> images = new HashMap<>(RegistryImages.read(LINEAGE));
        RegistryImage base = images.get(S2I_BASE);
        images.put(S2I_BASE, new RegistryImage(base.configDigest(), base.names(), base.parent(), base.blobShas(),
                base.layers(), base.created(), base.lastPushed(), "sha256:0b1c", base.labels(),
                base.candidates(), base.size(), base.architectures(),
                base.provenance(), base.usage()));

        assertThat(hierarchies(images, 40).built(PYTHON, Set.of()).blockedBy())
                .extracting(Hierarchies.Step::configDigest).containsExactly(S2I_BASE);
    }

    @Test
    void layersAreCountedFromTheImageWhenTheRegistryHasOne() {
        CatalogueEntry python = python();

        assertThat(hierarchies(RegistryImages.read(LINEAGE), 40).of(python, Map.of(), List.of(), Set.of())
                .layers()).isEqualTo(new Hierarchies.Layers(1, 4, 5, "image", false));
    }

    @Test
    void withoutAnImageLayersAreDeclaredWithInferredAncestorsAsInherited() {
        CatalogueEntry base = withLayers(CatalogueLinksTest.entry("aaa", "registry.access.redhat.com/ubi9/ubi:9.6",
                Map.of("name", "acme/base")), 3, List.of());
        CatalogueEntry app = withLayers(CatalogueLinksTest.entry("bbb", "registry.example.com/acme/base:1",
                Map.of("name", "acme/app")), 2, List.of());
        List<CatalogueEntry> entries = List.of(base, app);

        assertThat(hierarchies(Map.of(), 40).of(app, CatalogueEntries.bySha(entries), List.of("aaa"), Set.of())
                .layers())
                .isEqualTo(new Hierarchies.Layers(2, 3, 5, "declared", false));
    }

    @Test
    void anImageOverTheLayerLimitIsFlagged() {
        CatalogueEntry python = python();

        assertThat(hierarchies(RegistryImages.read(LINEAGE), 4).of(python, Map.of(), List.of(), Set.of())
                .layers().overLimit()).isTrue();
    }

    @Test
    void anEntryCarriesTheImagesBuiltFromItAndWhyItIsUnmaintained() {
        CatalogueEntry python = python();

        Hierarchies.Hierarchy hierarchy = hierarchies(RegistryImages.read(LINEAGE), 40)
                .of(python, Map.of(), List.of(), Set.of("sclorg/s2i-python-container"));

        assertAll(
                () -> assertThat(hierarchy.images()).extracting(Hierarchies.BuiltImage::configDigest)
                        .containsExactly(PYTHON),
                () -> assertThat(hierarchy.unmaintained()).containsExactly("every repository holding it is archived"));
    }

    @Test
    void anImageThisDockerfileMayHaveBuiltIsACandidateNotBuilt() {
        CatalogueEntry nginx = withLayers(CatalogueLinksTest.entry("6070555cb3c17f02425e461698624ec091d9ba5b",
                "quay.io/sclorg/s2i-core-c9s:c9s", Map.of()), 2, List.of());

        Hierarchies.Hierarchy hierarchy = hierarchies(RegistryImages.read(LINEAGE), 40)
                .of(nginx, Map.of(), List.of(), Set.of());

        assertAll(
                () -> assertThat(hierarchy.images()).isEmpty(),
                () -> assertThat(hierarchy.candidateImages()).containsExactly(RegistryImagesTest.NGINX));
    }

    @Test
    void anImageWithUnknownLayersLeavesTheLayersDeclared() {
        Map<String, RegistryImage> images = new HashMap<>(RegistryImages.read(LINEAGE));
        RegistryImage python = images.get(PYTHON);
        images.put(PYTHON, new RegistryImage(python.configDigest(), python.names(), python.parent(),
                python.blobShas(), null, python.created(), python.lastPushed(), null, python.labels(),
                python.candidates(), python.size(), python.architectures(),
                python.provenance(), python.usage()));
        CatalogueEntry entry = python();

        assertThat(hierarchies(images, 40).of(entry, Map.of(), List.of(), Set.of()).layers().counted())
                .isEqualTo("declared");
    }

    @Test
    void aDockerfileNoImageWasBuiltFromHasNoNewestImage() {
        assertThat(hierarchies(RegistryImages.read(LINEAGE), 40).newestImage("aaa")).isEmpty();
    }

    @Test
    void aLabelTheImageSetFromItsDockerfileComesFromTheDockerfile() {
        assertThat(Hierarchies.label("name", new ImageLabel("sclorg/python-312-c9s", PYTHON, null), PYTHON,
                Set.of("name"))).isEqualTo(new Hierarchies.Label("sclorg/python-312-c9s", "dockerfile", PYTHON));
    }

    @Test
    void aLabelTheImageSetWithoutItsDockerfileComesFromTheBuild() {
        assertThat(Hierarchies.label("release", new ImageLabel("7", PYTHON, null), PYTHON, Set.of("name")))
                .isEqualTo(new Hierarchies.Label("7", "build", PYTHON));
    }

    @Test
    void aLabelAnImageAboveSetComesFromThatAncestor() {
        assertThat(Hierarchies.label("version", new ImageLabel("9", S2I_BASE, null), PYTHON, Set.of("version")))
                .isEqualTo(new Hierarchies.Label("9", "ancestor", S2I_BASE));
    }

    @Test
    void aLabelTheExternalBaseSetComesFromTheBase() {
        assertThat(Hierarchies.label("vendor", new ImageLabel("Red Hat", null, "ubi9/ubi"), PYTHON, Set.of()))
                .isEqualTo(new Hierarchies.Label("Red Hat", "base", "ubi9/ubi"));
    }

    @Test
    void aLabelLineageCouldNotAttributeIsUnknown() {
        assertThat(Hierarchies.label("io.buildah.version", new ImageLabel("1.37.0", null, null), PYTHON, Set.of()))
                .isEqualTo(new Hierarchies.Label("1.37.0", "unknown", null));
    }

    @Test
    void eachImageBuiltFromAnEntryCarriesItsLabelsBySource() {
        CatalogueEntry python = withLayers(CatalogueLinksTest.entry(PYTHON_DOCKERFILE,
                "quay.io/sclorg/s2i-base-c9s:c9s", Map.of("name", "sclorg/python-312-c9s")), 1, List.of());

        Map<String, Hierarchies.Label> labels = hierarchies(RegistryImages.read(LINEAGE), 40)
                .of(python, Map.of(), List.of(), Set.of()).images().getFirst().labels();

        assertAll(
                () -> assertThat(labels.get("name").source()).isEqualTo("dockerfile"),
                () -> assertThat(labels.get("summary").source()).isEqualTo("build"),
                () -> assertThat(labels.get("org.label-schema.vendor").source()).isEqualTo("ancestor"),
                () -> assertThat(labels.get("io.buildah.version").source()).isEqualTo("unknown"));
    }

    @Test
    void layersAreOverTheLimitOnlyAboveIt() {
        Hierarchies hierarchies = hierarchies(Map.of(), 40);

        assertAll(
                () -> assertThat(hierarchies.limited(30, 10, "declared").overLimit()).isFalse(),
                () -> assertThat(hierarchies.limited(30, 11, "declared").overLimit()).isTrue());
    }

    @Test
    void aBuiltImageCarriesItsSizeArchitecturesAndCadence() {
        Map<String, RegistryImage> images = new HashMap<>(RegistryImages.read(LINEAGE));
        RegistryImage python = images.get(PYTHON);
        images.put(PYTHON, new RegistryImage(python.configDigest(), python.names(), python.parent(),
                python.blobShas(), python.layers(), python.created(), python.lastPushed(), null, python.labels(),
                python.candidates(), 39_000_000L, List.of("linux/amd64"), python.provenance(), python.usage()));

        Hierarchies.BuiltImage built = hierarchies(images, 40).built(PYTHON, Set.of());

        assertAll(
                () -> assertThat(built.size()).isEqualTo(39_000_000L),
                () -> assertThat(built.architectures()).containsExactly("linux/amd64"),
                () -> assertThat(built.cadence().daysAfterParent()).isZero(),
                () -> assertThat(built.provenance()).isEqualTo(new Provenance(false, false, false)),
                () -> assertThat(built.usage()).isEqualTo(new RegistryImage.Usage(null, null)));
    }

    @Test
    void eachDockerfileNamesTheImagesBuiltFromIt() {
        Map<String, RegistryImage> images = RegistryImages.read(LINEAGE);

        assertThat(Hierarchies.bySha(images.values(), RegistryImage::blobShas, RegistryImage::configDigest))
                .containsEntry(PYTHON_DOCKERFILE, List.of(PYTHON));
    }

    @Test
    void eachImageNamesItsChildrenAndTheRootIsNoonesChild() {
        Map<String, List<String>> children = Hierarchies.childrenOf(RegistryImages.read(LINEAGE).values());

        assertAll(
                () -> assertThat(children.get(S2I_BASE)).containsExactly(PYTHON),
                () -> assertThat(children.values()).noneMatch(list -> list.contains(CENTOS)));
    }

    @Test
    void theImagesBelowADockerfileAreEveryDescendantOfTheImagesBuiltFromIt() {
        assertThat(hierarchies(RegistryImages.read(LINEAGE), 40)
                .imagesBelow("84d379564c82cd28d8d358b5f94373bb49f0fb70")).isEqualTo(1);
    }

    @Test
    void aDockerfileNoImageWasBuiltFromHasNoImagesBelow() {
        assertThat(hierarchies(RegistryImages.read(LINEAGE), 40).imagesBelow("aaa")).isZero();
    }
}
