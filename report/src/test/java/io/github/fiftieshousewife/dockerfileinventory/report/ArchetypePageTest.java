package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchetypePageTest {

    private static final ArchetypeRows ROWS = ArchetypeRows.of(ArchetypeRowsTest.RANKING, ArchetypeRowsTest.ESTATE);
    private static final ArchetypeRows.Row UBI = ROWS.rows().getFirst();

    @Test
    void theSignatureIsATableOfAttributes() {
        assertThat(ArchetypePage.signature(UBI.archetype().signature()))
                .contains("<tr><th>Base family</th><td>ubi-minimal 9</td></tr>")
                .contains("<tr><th>User</th><td>numeric user</td></tr>");
    }

    @Test
    void organisationsAndRepositoriesAreCountedLargestFirst(@TempDir Path discovery) {
        assertThat(ArchetypePage.body(UBI, ReportRun.locations(ArchetypeRowsTest.ESTATE), discovery))
                .contains("<tr><td>acme</td><td class=\"num\">3</td>")
                .contains("<tr><td>acme/api</td><td class=\"num\">2</td>");
    }

    @Test
    void everyFileIsListedWithItsLocations(@TempDir Path discovery) {
        assertThat(ArchetypePage.body(UBI, ReportRun.locations(ArchetypeRowsTest.ESTATE), discovery))
                .contains("<li><code>a1</code>: acme/api: Dockerfile; acme/api: Dockerfile; acme/web: Dockerfile</li>");
    }

    @Test
    void theRepresentativeDockerfileIsFoldedWithSecretsHidden(@TempDir Path discovery) throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/a1"), "FROM ubi9\nENV DB_PASSWORD=hunter2\nRUN echo <hi>\n");

        assertThat(ArchetypePage.body(UBI, ReportRun.locations(ArchetypeRowsTest.ESTATE), discovery))
                .contains("<details><summary>The most used Dockerfile in it: acme/api: Dockerfile</summary>"
                        + "<pre>FROM ubi9\nENV DB_PASSWORD=(hidden)\nRUN echo &lt;hi&gt;\n</pre></details>")
                .doesNotContain("hunter2");
    }

    @Test
    void aMissingBlobHasNoExample(@TempDir Path discovery) {
        assertThat(ArchetypePage.example(discovery.resolve("blobs/none"))).isEmpty();
    }

    @Test
    void theRepresentativeIsTheArchetypesOwnUnlessItWasABundle() {
        assertThat(ArchetypePage.representative(UBI, ReportRun.locations(ArchetypeRowsTest.ESTATE))).isEqualTo("a1");
        assertThat(ArchetypePage.representative(ROWS.rows().get(1), ReportRun.locations(ArchetypeRowsTest.ESTATE)))
                .isEqualTo("c1");
    }
}
