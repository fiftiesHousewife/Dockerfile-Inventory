package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.junit.jupiter.api.Assertions.assertAll;
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

    @Test
    void aMissingValueIsAnEmptyCell() {
        assertAll(
                () -> assertThat(DockerfileSections.text(null)).isEmpty(),
                () -> assertThat(DockerfileSections.text(3)).isEqualTo("3"));
    }

    @Test
    void aCommandIsItsArgumentsAndItsForm() {
        assertAll(
                () -> assertThat(DockerfileSections.command(new CatalogueEntry.Command("EXEC", List.of("run", "-x"))))
                        .isEqualTo("run -x (EXEC)"),
                () -> assertThat(DockerfileSections.command(null)).isEmpty());
    }

    @Test
    void aStagesBaseIsItsNameAndTagOrNothing() {
        assertAll(
                () -> assertThat(DockerfileSections.base(stage("ubi9/ubi", "9.6", List.of(), List.of())))
                        .isEqualTo("ubi9/ubi:9.6"),
                () -> assertThat(DockerfileSections.base(stage("ubi9/ubi", null, List.of(), List.of())))
                        .isEqualTo("ubi9/ubi"),
                () -> assertThat(DockerfileSections.base(stage(null, null, List.of(), List.of()))).isEmpty());
    }

    @Test
    void aCopyFromAnotherStageNamesIt() {
        CatalogueEntry.Copy copy = new CatalogueEntry.Copy("COPY", List.of("app.jar"), "/app", "stage", "build",
                "1001", null);

        assertThat(DockerfileSections.copyTable(List.of(stage("ubi9/ubi", null, List.of(copy), List.of()))))
                .contains("<td>stage build</td>", "<td>1001</td>");
    }

    @Test
    void aRecursivePermissionChangeIsMarked() {
        CatalogueEntry.Permission change = new CatalogueEntry.Permission("chmod", "g+w", List.of("/app"), true);

        assertThat(DockerfileSections.permissionTable(List.of(stage("ubi9/ubi", null, List.of(), List.of(change)))))
                .contains("<td>chmod -R</td>");
    }

    @Test
    void aNamedStageShowsItsIndexAndName() {
        CatalogueEntry.StageEntry named = new CatalogueEntry.StageEntry(1, "build", "golang", null, null, false, null,
                List.of(), 0, 0, 0, 0, 0, List.of(), List.of(), List.of());

        assertThat(DockerfileSections.stageTable(List.of(named))).contains("<td>1 build</td>");
    }

    @Test
    void eachBuiltImageIsHeadedByItsNames() {
        assertThat(DockerfileSections.chain(Entries.image("sha256:app", Entries.step("sha256:base"))))
                .startsWith("<h3>sha256:app:1</h3>");
    }

    private static CatalogueEntry.StageEntry stage(String base, String tag, List<CatalogueEntry.Copy> copies,
                                                   List<CatalogueEntry.Permission> permissions) {
        return new CatalogueEntry.StageEntry(0, null, base, tag, null, false, null, List.of(), 0, 0, 0, 0, 0, copies,
                permissions, List.of());
    }
}
