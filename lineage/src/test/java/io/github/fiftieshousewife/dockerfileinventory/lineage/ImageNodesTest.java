package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageNodesTest {

    private static final String DOCKER_MANIFEST = "application/vnd.docker.distribution.manifest.v2+json";
    private static final String OCI_MANIFEST = "application/vnd.oci.image.manifest.v1+json";
    private static final String CONFIG_C = "sha256:" + "c".repeat(64);
    private static final String CONFIG_D = "sha256:" + "d".repeat(64);

    @Test
    void aSingleDockerOrOciManifestResolvesThroughItsConfigDigest() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .manifest("docker-local", "team/app", "1.0", DOCKER_MANIFEST, "manifest-image.json")
                .manifest("docker-local", "team/oci", "2.0", OCI_MANIFEST, "manifest-oci.json")
                .blob(CONFIG_C, "config.json")
                .blob(CONFIG_D, "config-bare.json");

        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/app", "1.0"))
                .isInstanceOfSatisfying(Resolved.Node.class, node -> {
                    assertThat(node.configDigest()).isEqualTo(CONFIG_C);
                    assertThat(node.config().user()).isEqualTo("1001");
                });
        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/oci", "2.0"))
                .isInstanceOfSatisfying(Resolved.Node.class, node -> assertThat(node.configDigest()).isEqualTo(CONFIG_D));
    }
}
