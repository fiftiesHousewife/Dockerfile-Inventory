package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineageDemoTest {

    private static final DemoRegistry.Ready READY = new DemoRegistry.Ready("docker-local", "t1");

    private final JcrContainersTest.Recorded docker = new JcrContainersTest.Recorded();

    private LineageDemo demo(Map<String, String> env) {
        JcrApi unused = new JcrApi(URI.create("http://127.0.0.1:1/"), "password", HttpClient.newHttpClient());
        return new LineageDemo(docker, new DemoRegistry(unused, new JcrContainers(docker, "jcr:7.77.17",
                "postgres:16.4", 8082), Waiter.immediate(), env), env);
    }

    private static BuildPlan.Build build(String name, String path, String base) {
        return new BuildPlan.Build(name, "sclorg/s2i-base-container", "c1", path, base);
    }

    @Test
    void withAMirrorListThePublishedImagesAreCopiedInsteadOfBuilt(@TempDir Path out) throws IOException {
        Path mirror = Files.writeString(out.resolve("mirror.txt"), "quay.io/okd/tools:5 okd/tools:5\n");

        demo(Map.of("MIRROR", mirror.toString())).images(out, out, out.resolve("build.log"), READY);

        assertThat(docker.asked).anyMatch(line -> line.contains(" copy -q --preserve-digests ")
                && line.endsWith("docker://inventory-jcr:8082/docker-local/okd/tools:5"));
    }

    @Test
    void withoutOneThePlansChainIsWrittenClonedAndBuilt(@TempDir Path out) throws IOException {
        Path discovery = Files.createDirectories(out.resolve("discovery"));
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/b1"), "FROM quay.io/centos/centos:stream9\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), "{\"repository\":\"sclorg/s2i-base-container\","
                + "\"commitSha\":\"c1\",\"path\":\"core/Dockerfile.c9s\",\"blobSha\":\"b1\"}\n");

        demo(Map.of()).images(discovery, out, out.resolve("build.log"), READY);

        assertAll(
                () -> assertThat(out.resolve("plan.txt.ordered")).content().startsWith("sclorg/s2i-core-c9s:c9s\t"),
                () -> assertThat(docker.asked).anyMatch(line -> line.startsWith("git clone")),
                () -> assertThat(docker.asked).anyMatch(line -> line.contains("buildah build")));
    }

    @Test
    void thePlanIsTheChainUnlessTheScopeIsAll() {
        List<BuildPlan.Build> all = List.of(build("sclorg/s2i-base-c9s:c9s", "base/Dockerfile.c9s",
                "quay.io/sclorg/s2i-core-c9s:c9s"), build("other", "other/Dockerfile.c9s", "fedora"));

        assertAll(
                () -> assertThat(demo(Map.of()).plan(all)).extracting(BuildPlan.Build::name)
                        .containsExactly("sclorg/s2i-base-c9s:c9s"),
                () -> assertThat(demo(Map.of("SCOPE", "all")).plan(all)).extracting(BuildPlan.Build::name)
                        .containsExactly("other", "sclorg/s2i-base-c9s:c9s"));
    }

    @Test
    void anInputMissingIsRefusedSayingWhatToRunFirst(@TempDir Path out) {
        assertThatIllegalArgumentException().isThrownBy(() -> LineageDemo.require(out.resolve("manifest.jsonl"),
                "run discovery first")).withMessage("no " + out.resolve("manifest.jsonl") + "; run discovery first");
    }

    @Test
    void aRunWithoutDiscoveryStopsBeforeStartingAnything(@TempDir Path out) {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> demo(Map.of("DISCOVERY_DIR",
                        out.toString())).run()).withMessageEndingWith("; run discovery first"),
                () -> assertThat(docker.asked).isEmpty());
    }

    @Test
    void theEnvironmentsSettingsMakeADemo() {
        assertThat(LineageDemo.of(Map.of("JCR_PORT", "18082"))).isNotNull();
    }

    @Test
    void aRunWithItsInputsStartsWithTheRegistry(@TempDir Path out) throws IOException {
        Files.writeString(out.resolve("manifest.jsonl"), "");
        Files.writeString(out.resolve("facts.jsonl"), "");

        assertThatIllegalStateException().isThrownBy(() -> demo(Map.of("DISCOVERY_DIR", out.toString(), "FACTS_DIR",
                out.toString(), "OUT", out.toString())).run()).withMessageStartingWith("Docker may use 0 GB");
    }
}
