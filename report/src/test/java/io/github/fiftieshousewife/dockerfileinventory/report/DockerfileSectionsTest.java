package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DockerfileSectionsTest {

    @Test
    void anImageWithNoUserWrittenSaysSoRatherThanNull() {
        CatalogueEntry plain = Entries.plain("aaa", "team/a");
        CatalogueEntry noUser = new CatalogueEntry("aaa", plain.locations(), false,
                new CatalogueEntry.Image(null, "ROOT", List.of(), null, null, null, List.of(), null, List.of(), null,
                        Map.of(), Map.of()),
                plain.build(), plain.links(), plain.footprint(), plain.hierarchy(), null, null);

        assertThat(DockerfileSections.image(noUser)).contains("<td>not set</td>").doesNotContain("null");
    }

    @Test
    void aChainStepWithNoDatesLeavesTheirCellsEmpty() {
        CatalogueEntry entry = Entries.entry("aaa", "team/a", "NUMERIC", List.of(), "minimal", 10, 0, List.of(),
                List.of(), List.of(), List.of(Entries.image("sha256:app", Entries.step("sha256:old"))));

        assertThat(DockerfileSections.images(entry)).doesNotContain("null");
    }
}
