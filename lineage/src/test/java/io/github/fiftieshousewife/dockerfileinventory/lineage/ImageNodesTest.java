package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ImageNodesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DOCKER_MANIFEST = "application/vnd.docker.distribution.manifest.v2+json";
    private static final String OCI_MANIFEST = "application/vnd.oci.image.manifest.v1+json";
    private static final String DOCKER_LIST = "application/vnd.docker.distribution.manifest.list.v2+json";
    private static final String OCI_INDEX = "application/vnd.oci.image.index.v1+json";
    private static final String CONFIG_C = "sha256:" + "c".repeat(64);
    private static final String CONFIG_D = "sha256:" + "d".repeat(64);

    @Test
    void theAmd64EntryIsTheLinuxAmd64DigestOfAList() {
        assertAll(
                () -> assertThat(ImageNodes.amd64(JSON.readTree(Fixtures.artifactory("manifest-list.json"))))
                        .contains("sha256:" + "1".repeat(64)),
                () -> assertThat(ImageNodes.amd64(JSON.readTree(Fixtures.artifactory("list-arm-only.json"))))
                        .isEmpty());
    }

    @Test
    void locatingATagGivesItsConfigDigestAndLayerSizesWithoutFetchingTheConfig() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .manifest("docker-local", "team/app", "1.0", DOCKER_MANIFEST, "manifest-image.json");

        Location located = ImageNodes.locate(artifactory, "docker-local", "team/app", "1.0");

        assertAll(
                () -> assertThat(located).isEqualTo(new Location.Found(CONFIG_C, 39_000_000L, List.of())),
                () -> assertThat(artifactory.blobCalls).isEmpty());
    }

    @Test
    void throughAListTheAmd64EntrysManifestGivesTheConfigOrIsGone() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .manifest("docker-local", "team/app", "sha256:" + "1".repeat(64), DOCKER_MANIFEST,
                        "manifest-image.json");
        JsonNode list = JSON.readTree(Fixtures.artifactory("manifest-list.json"));

        assertAll(
                () -> assertThat(ImageNodes.throughList(artifactory, "docker-local", "team/app", list, "ref"))
                        .isEqualTo(new Location.Found(CONFIG_C, 39_000_000L, List.of("linux/amd64", "linux/arm64"))),
                () -> assertThat(ImageNodes.throughList(new FakeArtifactory(), "docker-local", "team/app", list,
                        "ref")).isEqualTo(new Resolved.Skipped(Resolved.Reason.GONE, "sha256:" + "1".repeat(64))),
                () -> assertThat(ImageNodes.throughList(artifactory, "docker-local", "team/app",
                        JSON.readTree(Fixtures.artifactory("list-arm-only.json")), "ref"))
                        .isEqualTo(new Resolved.Skipped(Resolved.Reason.NO_AMD64, "ref")));
    }

    @Test
    void aNodeIsTheParsedConfigOrGoneWhenTheBlobIsMissing() {
        FakeArtifactory artifactory = new FakeArtifactory().blob(CONFIG_C, "config.json");

        assertThat(ImageNodes.node(artifactory, "docker-local", "team/app", CONFIG_C))
                .isInstanceOf(Resolved.Node.class);
        assertThat(ImageNodes.node(artifactory, "docker-local", "team/app", CONFIG_D))
                .isEqualTo(new Resolved.Skipped(Resolved.Reason.GONE, CONFIG_D));
    }

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
                .isInstanceOfSatisfying(Resolved.Node.class, node -> assertThat(node.configDigest())
                        .isEqualTo(CONFIG_D));
    }

    @Test
    void aManifestListOrIndexResolvesThroughItsLinuxAmd64Entry() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .manifest("docker-local", "team/app", "multi", DOCKER_LIST, "manifest-list.json")
                .manifest("docker-local", "team/app", "sha256:" + "1".repeat(64), DOCKER_MANIFEST,
                        "manifest-image.json")
                .manifest("docker-local", "team/oci", "multi", OCI_INDEX, "index-oci.json")
                .manifest("docker-local", "team/oci", "sha256:" + "e".repeat(64), OCI_MANIFEST, "manifest-oci.json")
                .blob(CONFIG_C, "config.json")
                .blob(CONFIG_D, "config-bare.json");

        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/app", "multi"))
                .isInstanceOfSatisfying(Resolved.Node.class, node -> assertThat(node.configDigest())
                        .isEqualTo(CONFIG_C));
        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/oci", "multi"))
                .isInstanceOfSatisfying(Resolved.Node.class, node -> assertThat(node.configDigest())
                        .isEqualTo(CONFIG_D));
    }

    @Test
    void aListWithNoLinuxAmd64EntryIsSkipped() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .manifest("docker-local", "team/arm", "1.0", DOCKER_LIST, "list-arm-only.json");

        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/arm", "1.0"))
                .isEqualTo(new Resolved.Skipped(Resolved.Reason.NO_AMD64, "docker-local/team/arm:1.0"));
    }

    @Test
    void aSchema1ManifestIsSkipped() {
        FakeArtifactory artifactory = new FakeArtifactory().manifest("docker-local", "team/legacy", "0.9",
                "application/vnd.docker.distribution.manifest.v1+prettyjws", "manifest-schema1.json");

        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/legacy", "0.9"))
                .isEqualTo(new Resolved.Skipped(Resolved.Reason.SCHEMA_1, "docker-local/team/legacy:0.9"));
    }

    @Test
    void aTagListEntryOrConfigThatHasGoneIsSkippedNamingWhatWentMissing() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .manifest("docker-local", "team/app", "multi", DOCKER_LIST, "manifest-list.json")
                .manifest("docker-local", "team/app", "1.0", DOCKER_MANIFEST, "manifest-image.json");

        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/app", "gone"))
                .isEqualTo(new Resolved.Skipped(Resolved.Reason.GONE, "docker-local/team/app:gone"));
        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/app", "multi"))
                .isEqualTo(new Resolved.Skipped(Resolved.Reason.GONE, "sha256:" + "1".repeat(64)));
        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/app", "1.0"))
                .isEqualTo(new Resolved.Skipped(Resolved.Reason.GONE, CONFIG_C));
    }

    @Test
    void anUnreadableConfigIsSkippedNamingItsDigest() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .manifest("docker-local", "team/app", "1.0", DOCKER_MANIFEST, "manifest-image.json")
                .blob(CONFIG_C, "not-json.txt");

        assertThat(ImageNodes.resolve(artifactory, "docker-local", "team/app", "1.0"))
                .isEqualTo(new Resolved.Skipped(Resolved.Reason.UNREADABLE_CONFIG, CONFIG_C));
    }
}
