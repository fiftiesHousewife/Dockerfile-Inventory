package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceLabelsTest {

    @Test
    void openShiftsSourceLocationNamesTheRepository() {
        assertThat(SourceLabels.repository(Map.of("io.openshift.build.source-location",
                "https://github.com/openshift/cluster-storage-operator")))
                .contains("openshift/cluster-storage-operator");
    }

    @Test
    void theOciSourceLabelNamesTheRepositoryEvenAsAPathInsideItOrWithDotGit() {
        assertThat(SourceLabels.repository(Map.of("org.opencontainers.image.source",
                "https://github.com/bitnami/containers/tree/main/bitnami/acmesolver"))).contains("bitnami/containers");
        assertThat(SourceLabels.repository(Map.of("org.opencontainers.image.source",
                "https://ghe.example.com/payments/api.git"))).contains("payments/api");
    }

    @Test
    void openShiftsLabelWinsAndAnImageWithoutEitherHasNoRepository() {
        assertThat(SourceLabels.repository(Map.of("io.openshift.build.source-location", "https://github.com/a/b",
                "org.opencontainers.image.source", "https://github.com/c/d"))).contains("a/b");
        assertThat(SourceLabels.repository(Map.of("vendor", "Red Hat, Inc."))).isEmpty();
        assertThat(SourceLabels.repository(Map.of("org.opencontainers.image.source", "not a url"))).isEmpty();
    }

    @Test
    void aUrlGivesItsFirstTwoPathSegmentsWithoutDotGit() {
        assertThat(SourceLabels.fromUrl(" https://ghe.example.com/org/name.git/tree/main ")).contains("org/name");
        assertThat(SourceLabels.fromUrl("https://ghe.example.com/org")).isEmpty();
        assertThat(SourceLabels.fromUrl("mailto:someone")).isEmpty();
        assertThat(SourceLabels.fromUrl("not a url")).isEmpty();
    }
}
