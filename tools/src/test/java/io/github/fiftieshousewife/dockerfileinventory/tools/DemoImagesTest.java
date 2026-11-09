package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoImagesTest {

    @TempDir
    Path out;

    private final JcrContainersTest.Recorded docker = new JcrContainersTest.Recorded();
    private final DemoImages images = new DemoImages(docker, "inventory-jcr:8082/docker-local", "password",
            "skopeo:1.16", "buildah:1.37", "linux/arm64");

    @Test
    void eachPublishedImageIsCopiedUnchangedAsAmd64AndLogged() throws IOException {
        docker.answers.put("docker run --rm --network inventory-demo skopeo:1.16 copy -q --preserve-digests "
                + "--override-os linux --override-arch amd64 --dest-tls-verify=false --dest-creds admin:password "
                + "docker://quay.io/bad", new Commands.Result(1, "manifest unknown"));

        images.mirror(List.of("quay.io/okd/tools:5 okd/tools:5", "", "quay.io/bad bad:1"), out.resolve("build.log"));

        assertAll(
                () -> assertThat(docker.asked).hasSize(2),
                () -> assertThat(docker.asked.getFirst()).endsWith("docker://quay.io/okd/tools:5 "
                        + "docker://inventory-jcr:8082/docker-local/okd/tools:5"),
                () -> assertThat(Files.readAllLines(out.resolve("build.log"))).containsExactly(
                        "pushed inventory-jcr:8082/docker-local/okd/tools:5", "FAILED quay.io/bad"));
    }

    @Test
    void eachRepositoryIsClonedOnceAtItsCommitWithItsSubmodules() {
        images.clone(List.of(new BuildPlan.Build("a", "sclorg/s2i-base-container", "c1", "core/Dockerfile.c9s", "x"),
                new BuildPlan.Build("b", "sclorg/s2i-base-container", "c1", "base/Dockerfile.c9s", "y")),
                out.resolve("src"));

        assertThat(docker.asked).hasSize(3).first().asString().startsWith("git clone -q --filter=blob:none "
                + "--no-checkout https://github.com/sclorg/s2i-base-container.git");
    }

    @Test
    void theCentosRootsAreMirroredThenEachDockerfileBuiltFromItsBaseInJcrAndPushed() throws IOException {
        images.build(List.of(new BuildPlan.Build("sclorg/s2i-core-c9s:c9s", "sclorg/s2i-base-container", "c1",
                "core/Dockerfile.c9s", "quay.io/centos/centos:stream9")), out, out.resolve("build.log"));

        assertAll(
                () -> assertThat(docker.asked).anyMatch(line -> line.endsWith("buildah pull -q --platform linux/amd64 "
                        + "quay.io/centos/centos:stream9")),
                () -> assertThat(docker.asked).anyMatch(line -> line.contains("buildah build -q --layers=false "
                        + "--platform linux/amd64 --tls-verify=false --creds admin:password --from "
                        + "inventory-jcr:8082/docker-local/centos/centos:stream9 -f /src/sclorg/s2i-base-container/c1/"
                        + "core/Dockerfile.c9s -t inventory-jcr:8082/docker-local/sclorg/s2i-core-c9s:c9s")),
                () -> assertThat(Files.readAllLines(out.resolve("build.log")))
                        .contains("pushed inventory-jcr:8082/docker-local/sclorg/s2i-core-c9s:c9s"));
    }

    @Test
    void aBaseResolvesInsideJcrWithoutQuayAndWithSclorgsTagWhenItHasNone() {
        assertAll(
                () -> assertThat(images.local("quay.io/sclorg/s2i-core-c9s"))
                        .isEqualTo("inventory-jcr:8082/docker-local/sclorg/s2i-core-c9s:c9s"),
                () -> assertThat(images.local("quay.io/centos/centos:stream9"))
                        .isEqualTo("inventory-jcr:8082/docker-local/centos/centos:stream9"));
    }
}
