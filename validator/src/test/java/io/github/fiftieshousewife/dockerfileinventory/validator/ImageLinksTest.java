package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageLinksTest {

    static final ImageLinks LINKS = ImageLinks.read(Path.of("src/test/resources/fixtures/lineage"));
    private static final String INSPECTOR = "2fc5b392e3ec9072112324117c989bdee6934113";
    private static final String POSTGRES = "141f718e54c8b80c550646a4ea3d0bff996df176";

    private static ContainerLink link(String image) {
        return LINKS.link(named("c", image, NOTHING));
    }

    @Test
    void aReferenceLinksOnItsPathAndTagWhateverTheHost() {
        assertAll(
                () -> assertThat(link("mirror.example/team/postgres:9.6"))
                        .isEqualTo(new ContainerLink("c", "mirror.example/team/postgres:9.6", List.of(POSTGRES), false,
                                null)),
                () -> assertThat(link("team/postgres:9.6").dockerfiles()).containsExactly(POSTGRES));
    }

    @Test
    void aReferenceMayNameTheArtifactoryRepositoryBeforeThePath() {
        assertThat(link("artifactory.example/docker-local/team/postgres:9.6").dockerfiles())
                .containsExactly(POSTGRES);
    }

    @Test
    void aFloatingReferenceLinksToTheImageItsTagNamedAndSaysItFloats() {
        ContainerLink inspector = link("docker.io/openshift/image-inspector");

        assertAll(
                () -> assertThat(inspector.dockerfiles()).containsExactly(INSPECTOR),
                () -> assertThat(inspector.floating()).isTrue());
    }

    @Test
    void eachUnlinkedContainerSaysWhy() {
        assertAll(
                () -> assertThat(link("team/app:1.2").unlinked())
                        .isEqualTo("lineage credits no Dockerfile with team/app:1.2"),
                () -> assertThat(link("rhel7").unlinked()).isEqualTo("lineage read no image rhel7:latest"),
                () -> assertThat(link("team/postgres@sha256:ab").unlinked())
                        .isEqualTo("named by digest alone, which lineage does not keep"),
                () -> assertThat(link(" ").unlinked()).isEqualTo("the manifest names no image"),
                () -> assertThat(ImageLinks.NONE.link(named("c", "team/postgres:9.6", NOTHING)).unlinked())
                        .isEqualTo("no lineage"));
    }

    @Test
    void anUnlinkedContainerHasNoDockerfiles() {
        assertThat(link("rhel7").dockerfiles()).isEmpty();
    }

    @Test
    void eachImageIsKnownByItsPathAndByItsRepositoryAndPath() {
        assertThat(ImageLinks.names("docker-local", "team/app", "1.2"))
                .containsExactly("team/app:1.2", "docker-local/team/app:1.2");
    }
}
