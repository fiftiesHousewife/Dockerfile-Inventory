package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class CatalogueEntriesTest {

    private static final Hierarchies.Hierarchy HIERARCHY = new Hierarchies.Hierarchy(
            new Hierarchies.Layers(3, 0, 3, "declared", false), List.of(), List.of(), List.of());

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/catalogue");
    private static final String SHA = "3f6d0c1a2b4e5f60718293a4b5c6d7e8f9a0b1c2";
    private static final JsonNode ROW = JsonLines.read(FIXTURES.resolve("facts.jsonl")).getFirst();

    private static CatalogueEntry entry() {
        return CatalogueEntries.of(ROW, CatalogueEntries.places(FIXTURES).get(SHA));
    }

    @Test
    void anEntryNamesItsFileAndEveryPlaceItLives() {
        CatalogueEntry entry = entry();

        assertAll(
                () -> assertThat(entry.blobSha()).isEqualTo(SHA),
                () -> assertThat(entry.locations()).containsExactly(
                        new CatalogueEntry.Place("payments/server", "Dockerfile", "RELEASED", "abc123",
                                "2026-03-02T10:11:12Z", "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"),
                        new CatalogueEntry.Place("payments/server-fork", "build/Dockerfile", "DEFAULT_BRANCH",
                                "def456", null, null)),
                () -> assertThat(entry.bundle()).isFalse());
    }

    @Test
    void theImageIsWhatRunsUserPortsStartConfigurationEnvironmentAndLabels() {
        CatalogueEntry.Image image = entry().image();

        assertAll(
                () -> assertThat(image.user()).isEqualTo("1001"),
                () -> assertThat(image.userKind()).isEqualTo("NUMERIC"),
                () -> assertThat(image.ports()).containsExactly("8080", "9090/udp"),
                () -> assertThat(image.entrypoint())
                        .isEqualTo(new CatalogueEntry.Command("EXEC", List.of("/usr/local/bin/server"))),
                () -> assertThat(image.cmd()).isEqualTo(new CatalogueEntry.Command("EXEC", List.of("--port", "8080"))),
                () -> assertThat(image.workdir()).isEqualTo("/opt/app"),
                () -> assertThat(image.volumes()).containsExactly("/opt/app/data"),
                () -> assertThat(image.healthcheck()).startsWith("--interval=30s CMD"),
                () -> assertThat(image.environment()).containsEntry("APP_HOME", "/opt/app"),
                () -> assertThat(image.environment().get("API_TOKEN")).startsWith("(hidden sha256:"),
                () -> assertThat(image.labels()).containsExactly(Map.entry("name", "payments/server"),
                        Map.entry("version", "1.4.2")));
    }

    @Test
    void eachStageHasItsBaseItsCountsItsCopiesAndItsPermissionChanges() {
        assertThat(entry().build().stages()).containsExactly(
                new CatalogueEntry.StageEntry(0, "build", "registry.access.redhat.com/ubi9/go-toolset", "1.22", null,
                        false, null, List.of(), 3, 1, 1, 0, 2, List.of(new CatalogueEntry.Copy("COPY", List.of("."),
                                ".", "context", null, null, null)), List.of(), List.of()),
                new CatalogueEntry.StageEntry(1, null, "registry.access.redhat.com/ubi9/ubi-minimal", "9.4",
                        "sha256:1111111111111111111111111111111111111111111111111111111111111111", true, null,
                        List.of("build"), 11, 1, 2, 0, 2, List.of(new CatalogueEntry.Copy("COPY",
                                List.of("/out/server"), "/usr/local/bin/server", "stage", "build", null, null)),
                        List.of(), List.of("VOLUME", "HEALTHCHECK")));
    }

    @Test
    void eachInstalledPackageSaysItsManagerWhetherItIsVersionedAndWhetherItsRunCleaned() {
        assertThat(entry().build().packages())
                .containsExactly(new CatalogueEntry.PackageEntry(1, "microdnf", "tzdata-2024a", true, true));
    }

    @Test
    void theBuildNamesTheLanguageRuntimesAndWhetherTheyReachTheImage() {
        assertThat(entry().build().runtimes()).containsExactly(
                new Runtimes.Found("go", "build", "stage 0 base registry.access.redhat.com/ubi9/go-toolset"));
    }

    @Test
    void anEntryCarriesItsLinksFootprintAndHierarchyOnceTheyAreKnown() {
        CatalogueLinks.Links links = new CatalogueLinks.Links(List.of(), List.of(), 0);
        Footprint.Found footprint = new Footprint.Found("minimal", "ubi9/ubi-minimal", List.of());
        CatalogueEntry placed = entry().placed(new Placement(links, footprint, List.of(), HIERARCHY, null));

        assertAll(
                () -> assertThat(placed.links()).isEqualTo(links),
                () -> assertThat(placed.footprint()).isEqualTo(footprint),
                () -> assertThat(placed.hierarchy()).isEqualTo(HIERARCHY));
    }

    @Test
    void inheritedRuntimesFollowTheEntrysOwn() {
        Runtimes.Found java = new Runtimes.Found("java", "inherited", "from aaa: stage 0 base ubi9/openjdk-21");
        CatalogueEntry placed = entry().placed(new Placement(new CatalogueLinks.Links(List.of(), List.of(), 0),
                new Footprint.Found("minimal", "ubi9/ubi-minimal", List.of()), List.of(java), HIERARCHY, null));

        assertThat(placed.build().runtimes()).containsExactly(
                new Runtimes.Found("go", "build", "stage 0 base registry.access.redhat.com/ubi9/go-toolset"), java);
    }

    @Test
    void theBuildCountsLayersRunsUnreadRunsAndTheFinalImagesInstructions() {
        CatalogueEntry.Build build = entry().build();

        assertAll(
                () -> assertThat(build.layers()).isEqualTo(2),
                () -> assertThat(build.runs()).isEqualTo(2),
                () -> assertThat(build.opaqueRuns()).isZero(),
                () -> assertThat(build.instructions()).containsEntry("COPY", 1).containsEntry("ENV", 1)
                        .containsEntry("HEALTHCHECK", 1));
    }
}
