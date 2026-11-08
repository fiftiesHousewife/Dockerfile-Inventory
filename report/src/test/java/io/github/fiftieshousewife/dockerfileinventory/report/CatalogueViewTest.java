package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogueViewTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/catalogue");

    private static CatalogueView written(Path facts, Path out) {
        CatalogueRun.write(new CatalogueRun.Inputs(facts, FIXTURES, RegistryImagesTest.LINEAGE, null, List.of()), out,
                CatalogueSettings.DEFAULTS);
        return CatalogueView.read(out.resolve("catalogue/dockerfiles.jsonl"));
    }

    @Test
    void theEntriesTheCatalogueWroteReadBackAsTheyWere(@TempDir Path out) {
        CatalogueView view = written(FIXTURES, out);

        assertAll(
                () -> assertThat(view.entries()).extracting(CatalogueEntry::blobSha)
                        .containsExactly("3f6d0c1a2b4e5f60718293a4b5c6d7e8f9a0b1c2"),
                () -> assertThat(view.entries().getFirst().locations()).hasSize(2),
                () -> assertThat(view.entries().getFirst().build().stages()).hasSize(2),
                () -> assertThat(view.entries().getFirst().actions().releaseScope()).isEqualTo("released"));
    }

    @Test
    void aDockerfileThatDidNotParseReadsBackWithItsReason(@TempDir Path facts, @TempDir Path out)
            throws IOException {
        Files.writeString(facts.resolve("parse-failures.jsonl"),
                "{\"blobSha\":\"3f6d0c1a2b4e5f60718293a4b5c6d7e8f9a0b1c2\",\"message\":\"mismatched input\"}\n");

        assertThat(written(facts, out).entries().getFirst().parseError()).isEqualTo("mismatched input");
    }

    @Test
    void anEntrysOrganisationIsTheOwnerOfItsFirstLocation(@TempDir Path out) {
        assertThat(CatalogueView.organisation(written(FIXTURES, out).entries().getFirst())).isEqualTo("payments");
    }
}
