package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimesTest {

    private static CatalogueEntry.StageEntry stage(int index, String name, String base, String fromStage) {
        return new CatalogueEntry.StageEntry(index, name, base, null, null, false, fromStage, List.of(), 0, 0, 0, 0,
                0, List.of(), List.of(), List.of());
    }

    @Test
    void aRuntimeInABuildStageOnlyIsBuildOnlyAndOneInTheFinalImageIsFinal() {
        List<CatalogueEntry.StageEntry> stages = List.of(stage(0, "build", "registry.access.redhat.com/ubi9/go-toolset",
                null), stage(1, null, "registry.access.redhat.com/ubi9/openjdk-21-runtime", null));

        assertThat(Runtimes.of(stages, List.of(), Map.of())).containsExactly(
                new Runtimes.Found("go", "build", "stage 0 base registry.access.redhat.com/ubi9/go-toolset"),
                new Runtimes.Found("java", "final", "stage 1 base registry.access.redhat.com/ubi9/openjdk-21-runtime"));
    }

    @Test
    void packagesAndTheFinalImagesEnvironmentAreEvidenceToo() {
        List<CatalogueEntry.StageEntry> stages = List.of(stage(0, "base", "registry.access.redhat.com/ubi9/ubi", null),
                stage(1, null, null, "base"));
        List<CatalogueEntry.PackageEntry> packages = List.of(new CatalogueEntry.PackageEntry(0, "dnf", "nodejs",
                false, true), new CatalogueEntry.PackageEntry(0, "dnf", "python3.11", false, true));

        assertThat(Runtimes.of(stages, packages, Map.of("JAVA_HOME", "/usr/lib/jvm/jre"))).containsExactly(
                new Runtimes.Found("node", "final", "stage 0 installs nodejs"),
                new Runtimes.Found("python", "final", "stage 0 installs python3.11"),
                new Runtimes.Found("java", "final", "environment JAVA_HOME"));
    }

    @Test
    void theFinalImagesStagesAreTheLastAndThoseItBuildsOnByName() {
        assertAll(
                () -> assertThat(Runtimes.finalStages(List.of(stage(0, "a", "x", null), stage(1, "b", null, "a"),
                        stage(2, null, null, "b")))).containsExactly(2, 1, 0),
                () -> assertThat(Runtimes.finalStages(List.of(stage(0, "a", "x", null), stage(1, null, "y", null))))
                        .containsExactly(1));
    }

    @Test
    void aRuntimeInAnAncestorsFinalImageIsInheritedAndNamesTheAncestor() {
        CatalogueEntry base = CatalogueLinksTest.entry("aaa", "registry.access.redhat.com/ubi9/openjdk-21:1.20",
                Map.of("name", "acme/java-base"));
        CatalogueEntry app = CatalogueLinksTest.entry("bbb", "registry.example.com/acme/java-base:1",
                Map.of("name", "acme/app"));
        List<CatalogueEntry> entries = List.of(base, app);

        assertThat(Runtimes.inherited("bbb", entries, CatalogueLinks.of(entries, null))).containsExactly(
                new Runtimes.Found("java", "inherited",
                        "from aaa: stage 0 base registry.access.redhat.com/ubi9/openjdk-21"));
    }
}
