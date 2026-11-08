package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.image;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.step;
import static io.github.fiftieshousewife.dockerfileinventory.report.Entries.unparsed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class DockerfilePageTest {

    private static CatalogueEntry entry() {
        CatalogueEntry plain = Entries.entry("aaa", "payments/api", "ROOT", List.of("8080"), "minimal", 12, 1,
                List.of(new CatalogueEntry.PackageEntry(0, "dnf", "python3", false, true)), List.of("VOLUME"),
                List.of(new Runtimes.Found("python", "final", "stage 0 installs python3")),
                List.of(image("sha256:app", step("sha256:old", "not published since 2024-01-01"))));
        return new CatalogueEntry(plain.blobSha(), plain.locations(), false, plain.image(), plain.build(),
                new CatalogueLinks.Links(List.of(new CatalogueLinks.Link("bbb", "inferred", "base acme/base")),
                        List.of(new CatalogueLinks.Link("ccc", "lineage", "image built on it")), 1),
                plain.footprint(), plain.hierarchy(),
                new Actions("released", new Reaches.Reach(1, 1, 1, 0),
                        new Conversions.Conversion("passed", null, "not run", null),
                        new Siblings.Found(List.of("ddd"), 1), null),
                null);
    }

    @Test
    void aDockerfilesPageShowsWhereItIsWhatItBuildsAndWhatStandsAboveAndBelowIt() {
        String body = DockerfilePage.body(entry());

        assertAll(
                () -> assertThat(body).contains("payments/api", "Dockerfile"),
                () -> assertThat(body).contains("ubi9/ubi:9.6", "python3", "VOLUME", "python (final)"),
                () -> assertThat(body).contains("href=\"bbb.html\"", "href=\"ccc.html\"", "href=\"ddd.html\""),
                () -> assertThat(body).contains("sha256:app:1", "sha256:old:1", "not published since 2024-01-01"),
                () -> assertThat(body).contains("released", "passed"));
    }

    @Test
    void aDockerfileThatDidNotParseSaysWhy() {
        assertThat(DockerfilePage.body(unparsed("aaa", "payments/web"))).contains("payments/web", "mismatched input");
    }
}
