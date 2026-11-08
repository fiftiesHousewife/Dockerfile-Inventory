package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.entry;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.image;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.plain;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.step;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.unparsed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EstateFiguresTest {

    private static final CatalogueEntry ROOT_ON_80 = entry("aaa", "payments/api", "ROOT", List.of("80", "8443/tcp"),
            "full", 45, 2, List.of(new CatalogueEntry.PackageEntry(0, "dnf", "gcc", false, true)),
            List.of("VOLUME", "ADD"), List.of(new Runtimes.Found("java", "final", "base")),
            List.of(image("sha256:app", step("sha256:old", "not published since 2024-01-01"))));

    @Test
    void theCountsAreDockerfilesUnparsedLocationsImagesAndOrganisations() {
        EstateFigures figures = EstateFigures.of(List.of(ROOT_ON_80, plain("bbb", "platform/base"),
                unparsed("ccc", "payments/web")));

        assertAll(
                () -> assertThat(figures.dockerfiles()).isEqualTo(2),
                () -> assertThat(figures.unparsed()).isEqualTo(1),
                () -> assertThat(figures.locations()).isEqualTo(3),
                () -> assertThat(figures.images()).isEqualTo(1),
                () -> assertThat(figures.organisations()).isEqualTo(2));
    }

    @Test
    void depthsAndLayerBandsAreCountedPerDockerfile() {
        EstateFigures figures = EstateFigures.of(List.of(ROOT_ON_80, plain("bbb", "platform/base")));

        assertAll(
                () -> assertThat(figures.depths()).containsExactly(Map.entry(0, 1), Map.entry(2, 1)),
                () -> assertThat(figures.layerBands()).containsEntry("6-10", 1).containsEntry("over 40", 1),
                () -> assertThat(figures.overLimit()).isEqualTo(1));
    }

    @Test
    void rootLowPortsAndToolchainsAreCountedInTheFinalImage() {
        EstateFigures figures = EstateFigures.of(List.of(ROOT_ON_80, plain("bbb", "platform/base")));

        assertAll(
                () -> assertThat(figures.root()).isEqualTo(1),
                () -> assertThat(figures.lowPort()).isEqualTo(1),
                () -> assertThat(figures.toolchain()).isEqualTo(1));
    }

    @Test
    void footprintsRuntimesAndUnusualStepsAreTallied() {
        EstateFigures figures = EstateFigures.of(List.of(ROOT_ON_80, plain("bbb", "platform/base")));

        assertAll(
                () -> assertThat(figures.footprints()).containsEntry("full", 1).containsEntry("minimal", 1),
                () -> assertThat(figures.runtimes()).containsExactly(Map.entry("java (final)", 1)),
                () -> assertThat(figures.unusual()).containsEntry("VOLUME", 1).containsEntry("ADD", 1));
    }

    @Test
    void unmaintainedIntermediatesAreCountedByReasonWithTheImagesTheyBlock() {
        EstateFigures figures = EstateFigures.of(List.of(ROOT_ON_80));

        assertAll(
                () -> assertThat(figures.unmaintained()).containsExactly(Map.entry("not published for a while", 1)),
                () -> assertThat(figures.blockedImages()).isEqualTo(1));
    }

    @Test
    void aPortBelow1024IsLowWhateverItsProtocol() {
        assertAll(
                () -> assertThat(EstateFigures.lowPort(List.of("443/tcp"))).isTrue(),
                () -> assertThat(EstateFigures.lowPort(List.of("8080", "${PORT}"))).isFalse());
    }

    @Test
    void layerCountsFallIntoBands() {
        assertThat(List.of(3, 7, 15, 30, 41).stream().map(EstateFigures::layerBand).toList())
                .containsExactly("1-5", "6-10", "11-20", "21-40", "over 40");
    }
}
