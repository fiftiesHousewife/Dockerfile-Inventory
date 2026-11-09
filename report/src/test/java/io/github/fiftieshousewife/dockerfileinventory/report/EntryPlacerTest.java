package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntryPlacerTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/catalogue");
    private static final CatalogueEntry BASE = CatalogueLinksTest.entry("aaa",
            "registry.access.redhat.com/ubi9/ubi:9.4",
            Map.of("name", "acme/base"));
    private static final CatalogueEntry APP = CatalogueLinksTest.entry("bbb", "registry.example.com/acme/base:1",
            Map.of("name", "acme/app"));

    private static EntryPlacer placer(Path lineage) {
        return new EntryPlacer(List.of(BASE, APP), new CatalogueRun.Inputs(FIXTURES, FIXTURES, lineage, null,
                List.of()), CatalogueSettings.DEFAULTS);
    }

    @Test
    void aPlacedEntryCarriesItsLinksFootprintAndActions() {
        CatalogueEntry placed = placer(null).placed(APP);

        assertAll(
                () -> assertThat(placed.links().parents()).extracting(CatalogueLinks.Link::blobSha)
                        .containsExactly("aaa"),
                () -> assertThat(placed.footprint().base()).isEqualTo("registry.access.redhat.com/ubi9/ubi"),
                () -> assertThat(placed.actions()).isNotNull());
    }

    @Test
    void aFixToTheBaseReachesTheDockerfileBuiltOnIt() {
        assertThat(placer(null).actions(BASE).reach().dockerfiles()).isEqualTo(1);
    }

    @Test
    void withoutARegistryNoEntryHasImages() {
        assertThat(EntryPlacer.hierarchies(null, CatalogueSettings.DEFAULTS).imagesBelow("aaa")).isZero();
    }

    @Test
    void aBaseWithoutATagHasNoLag() {
        CatalogueEntry untagged = CatalogueLinksTest.entry("ccc", "registry.access.redhat.com/ubi9/ubi", Map.of());

        assertThat(placer(null).baseLag(untagged)).isNull();
    }

    @Test
    void aBaseBehindTheRegistrysNewestTagLags(@TempDir Path lineage) throws IOException {
        Files.writeString(lineage.resolve("images.jsonl"), """
                {"repository":"remote","image":"ubi9/ubi","tag":"9.4","configDigest":null}
                {"repository":"remote","image":"ubi9/ubi","tag":"9.6","configDigest":null}
                """);

        assertThat(placer(lineage).baseLag(BASE).newestTag()).isEqualTo("9.6");
    }
}
