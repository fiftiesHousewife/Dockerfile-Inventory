package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.Counts;
import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.ImageRow;
import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.OutdatedRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LineageSummaryTest {

    static final LineageView OKD = new LineageView(
            new Counts(5, 1.0, Map.of("WEAK", 3, "NONE", 2), 1, 1, 1),
            List.of(new ImageRow("docker-local/base:1", null, "NONE", List.of()),
                    new ImageRow("docker-local/app:1", "docker-local/base:1", "WEAK", List.of("org/app: Dockerfile")),
                    new ImageRow("docker-local/app:2", "docker-local/base:1", "WEAK", List.of("org/app: Dockerfile")),
                    new ImageRow("docker-local/cli:1", "docker-local/app:1", "WEAK", List.of("org/cli: Dockerfile")),
                    new ImageRow("docker-local/gap:1", "docker-local/base:1", "NONE", List.of())),
            List.of(), List.of("docker-local/gap:1"),
            List.of(new OutdatedRow("docker-local/app:1", "docker-local/base:1", "docker-local/base:2")));

    @Test
    void linkConfidenceIsOneBarOfTiersWithWhatEachMeans() {
        assertThat(LineageSummary.confidence(OKD.counts()))
                .contains("<span class=\"key s3\"></span>weak 3 (60.0%)", "<span class=\"key s4\"></span>none 2")
                .contains("the closest Dockerfile on the same base")
                .doesNotContain("strong 0");
    }

    @Test
    void imagesOnAnOutdatedBaseListTheImageItsBaseAndTheNewerBase() {
        assertThat(LineageSummary.outdated(OKD.outdated())).contains(
                "<h3>On an outdated base (1)</h3>",
                "<tr><td class=\"code\">docker-local/app:1</td><td class=\"code\">docker-local/base:1</td>"
                        + "<td class=\"code\">docker-local/base:2</td></tr>");
    }

    @Test
    void gapsListTheImagesWithNoDockerfile() {
        assertThat(LineageSummary.gaps(OKD.gaps()))
                .isEqualTo("<h3>No Dockerfile found (1)</h3><ul><li><code>docker-local/gap:1</code></li></ul>");
        assertThat(LineageSummary.gaps(List.of())).isEqualTo("<h3>No Dockerfile found (0)</h3>"
                + "<p class=\"note\">Every image with a parent in Artifactory has a Dockerfile.</p>");
    }

    @Test
    void theSectionLinksToTheTreeOrSaysLineageHasNotRun() {
        assertThat(LineageSummary.render(Optional.of(OKD), "")).contains("<a href=\"lineage.html\">")
                .contains("5 images in Artifactory");
        assertThat(LineageSummary.render(Optional.empty(), "")).contains("Lineage has not run");
    }
}
