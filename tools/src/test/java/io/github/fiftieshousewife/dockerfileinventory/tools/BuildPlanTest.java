package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildPlanTest {

    private static BuildPlan.Build build(String name, String repository, String path, String base) {
        return new BuildPlan.Build(name, repository, "c1", path, base);
    }

    @Test
    void eachC9sDockerfileIsNamedAsSclorgPublishesItWithTheBaseItIsBuiltFrom(@TempDir Path discovery)
            throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/b1"), "# s2i-core\nfrom quay.io/centos/centos:stream9 AS x\n");
        Files.writeString(discovery.resolve("blobs/b2"), "FROM quay.io/sclorg/s2i-core-c9s:c9s\n");
        Files.writeString(discovery.resolve("blobs/b3"), "FROM fedora\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), String.join("\n",
                "{\"repository\":\"sclorg/s2i-base-container\",\"commitSha\":\"c1\",\"path\":\"core/Dockerfile.c9s\","
                        + "\"blobSha\":\"b1\"}",
                "{\"repository\":\"sclorg/nginx-container\",\"commitSha\":\"c1\",\"path\":\"1.24/Dockerfile.c9s\","
                        + "\"blobSha\":\"b2\"}",
                "{\"repository\":\"sclorg/nginx-container\",\"commitSha\":\"c1\",\"path\":\"1.24/Dockerfile.fedora\","
                        + "\"blobSha\":\"b3\"}") + "\n");

        assertThat(BuildPlan.all(discovery)).containsExactly(
                build("sclorg/nginx-container-1-24-c9s:c9s", "sclorg/nginx-container", "1.24/Dockerfile.c9s",
                        "quay.io/sclorg/s2i-core-c9s:c9s"),
                build("sclorg/s2i-core-c9s:c9s", "sclorg/s2i-base-container", "core/Dockerfile.c9s",
                        "quay.io/centos/centos:stream9"));
    }

    @Test
    void theChainIsS2iCoreAndBaseWithPython312AndNginx124AndIsBuiltParentsFirst() {
        List<BuildPlan.Build> all = List.of(
                build("sclorg/s2i-python-container-3-12-c9s:c9s", "sclorg/s2i-python-container", "3.12/Dockerfile.c9s",
                        "quay.io/sclorg/s2i-base-c9s:c9s"),
                build("sclorg/s2i-python-container-3-11-c9s:c9s", "sclorg/s2i-python-container", "3.11/Dockerfile.c9s",
                        "quay.io/sclorg/s2i-base-c9s:c9s"),
                build("sclorg/s2i-base-c9s:c9s", "sclorg/s2i-base-container", "base/Dockerfile.c9s",
                        "quay.io/sclorg/s2i-core-c9s:c9s"),
                build("sclorg/s2i-core-c9s:c9s", "sclorg/s2i-base-container", "core/Dockerfile.c9s",
                        "quay.io/centos/centos:stream9"));

        List<BuildPlan.Build> chain = BuildPlan.chain(all);
        assertAll(
                () -> assertThat(chain).hasSize(3),
                () -> assertThat(BuildPlan.parentsFirst(chain)).extracting(BuildPlan.Build::name).containsExactly(
                        "sclorg/s2i-core-c9s:c9s", "sclorg/s2i-base-c9s:c9s",
                        "sclorg/s2i-python-container-3-12-c9s:c9s"));
    }

    @Test
    void aBuildRanksByHowFarDownTheChainItsBaseIs() {
        assertAll(
                () -> assertThat(BuildPlan.rank(build("a", "r", "p", "quay.io/centos/centos:stream9"))).isZero(),
                () -> assertThat(BuildPlan.rank(build("a", "r", "p", "quay.io/sclorg/s2i-core-c9s:c9s"))).isEqualTo(1),
                () -> assertThat(BuildPlan.rank(build("a", "r", "p", "quay.io/sclorg/s2i-base-c9s:c9s"))).isEqualTo(2));
    }

    @Test
    void aDockerfileWithoutAFromHasNoBase(@TempDir Path blobs) throws IOException {
        assertThat(BuildPlan.firstFrom(Files.writeString(blobs.resolve("b"), "# only a comment\nFROM\n"))).isEmpty();
    }
}
