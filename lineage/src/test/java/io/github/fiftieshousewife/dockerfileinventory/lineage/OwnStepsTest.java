package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OwnStepsTest {

    /** Shared with facts, which must reduce the Dockerfile that built this image to the same steps. */
    private static final Path CONTRACT = Path.of("../testdata/contracts/python-311.steps.json");

    private static final HistoryStep BASE = step("/bin/sh -c #(nop) ADD file:base in / ", null);
    private static final HistoryStep APP_FROM =
            step("/bin/sh -c #(nop) EXPOSE 8080", "FROM registry.example.com/base:1");
    private static final HistoryStep APP_RUN = step("/bin/sh -c make", null);
    private static final HistoryStep BUILDINFO = step("/bin/sh -c #(nop) COPY file:1 in /root/buildinfo/x.json ", null);

    @Test
    void theOwnStepsFollowTheParentsHistoryWithBuilderStepsDropped() {
        Resolved.Node parent = node(List.of(BASE, APP_FROM));
        Resolved.Node child = node(List.of(BASE, APP_FROM, BUILDINFO, APP_RUN));

        assertThat(OwnSteps.of(child, parent)).containsExactly(new Instruction("RUN", "make"));
    }

    @Test
    void argStepsAreLeftOutAsFactsLeavesThemOutOfADockerfilesSteps() {
        Resolved.Node image = node(List.of(BASE, APP_FROM, step("/bin/sh -c #(nop) ARG WORK_DIR", null),
                step("|1 WORK_DIR=/data /bin/sh -c mkdir $WORK_DIR", null)));

        assertThat(OwnSteps.of(image, null))
                .containsExactly(new Instruction("EXPOSE", "8080"), new Instruction("RUN", "mkdir $WORK_DIR"));
    }

    @Test
    void withNoParentTheOwnStepsStartAtTheLastFromComment() {
        Resolved.Node image = node(List.of(BASE, APP_FROM, APP_RUN, BUILDINFO));

        assertThat(OwnSteps.of(image, null))
                .containsExactly(new Instruction("EXPOSE", "8080"), new Instruction("RUN", "make"));
    }

    @Test
    void withNoParentAndNoFromCommentTheWholeHistoryIsTheImagesOwn() {
        assertThat(OwnSteps.of(node(List.of(BASE, APP_RUN)), null))
                .containsExactly(new Instruction("ADD", "/"), new Instruction("RUN", "make"));
    }

    @Test
    void anOkdImageGivesItsDockerfilesStepsWithoutTheReleaseToolingAroundThem() {
        Resolved.Node image = new Resolved.Node("sha256:okd",
                ImageConfig.parse(Fixtures.history("okd-cluster-storage-operator.json")).orElseThrow());

        assertThat(OwnSteps.of(image, null)).containsExactly(
                new Instruction("COPY", "/usr/bin/"),
                new Instruction("COPY", "/usr/bin/"),
                new Instruction("COPY", "/manifests"),
                new Instruction("ENTRYPOINT", "[\"/usr/bin/cluster-storage-operator\"]"),
                new Instruction("LABEL", "com.redhat.component description io.k8s.description io.k8s.display-name "
                        + "io.openshift.build.commit.id io.openshift.build.commit.url "
                        + "io.openshift.build.source-location io.openshift.maintainer.component "
                        + "io.openshift.release.operator io.openshift.tags name release summary vendor version"));
    }

    @Test
    void theReleaseToolingCutKeepsADockerfilesOwnUserZeroAndWhatFollowsTheWrapper() {
        NormalStep doozer = new NormalStep("ENV", "OS_GIT_COMMIT __doozer", null, false);
        NormalStep root = new NormalStep("USER", "0", null, false);
        NormalStep make = new NormalStep("RUN", "make", null, false);
        NormalStep swap = new NormalStep("RUN", "rm -f /etc/yum.repos.d/art-* && mv /tmp/art/yum_temp/* x || true",
                null, false);
        NormalStep cleanup = new NormalStep("RUN", "rm -rf /tmp/art", null, false);
        NormalStep user = new NormalStep("USER", "1001", null, false);

        assertThat(OwnSteps.withoutReleaseTooling(List.of(root, doozer, root, make, root, swap, cleanup, user)))
                .containsExactly(root, make, user);
        assertThat(OwnSteps.withoutReleaseTooling(List.of(root, make))).containsExactly(root, make);
    }

    @Test
    void thePublicPython311HistoryGivesTheStepsOfTheDockerfileThatBuiltIt() throws IOException {
        JsonNode contract = new ObjectMapper().readTree(CONTRACT.toFile());
        List<Instruction> expected = new ArrayList<>();
        contract.path("steps").forEach(s -> expected.add(
                new Instruction(s.path("keyword").asString(), s.path("text").asString())));
        Resolved.Node image = new Resolved.Node("sha256:python",
                ImageConfig.parse(Fixtures.history("ubi9-python-311.json")).orElseThrow());

        assertThat(OwnSteps.of(image, null)).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "s2i-core,   sclorg_s2i-core-c9s_c9s,                  centos_centos_stream9",
        "s2i-base,   sclorg_s2i-base-c9s_c9s,                  sclorg_s2i-core-c9s_c9s",
        "python-312, sclorg_s2i-python-container-3-12-c9s_c9s, sclorg_s2i-base-c9s_c9s",
        "nginx-124,  sclorg_nginx-container-1-24-c9s_c9s,      sclorg_s2i-core-c9s_c9s"})
    void eachRecordedBuildahImageGivesTheStepsOfTheDockerfileItWasBuiltFrom(String name, String image, String parent)
            throws IOException {
        JsonNode contract = new ObjectMapper().readTree(
                Path.of("../testdata/contracts/sclorg-c9s-" + name + ".steps.json").toFile());
        List<Instruction> expected = new ArrayList<>();
        contract.path("steps").forEach(s -> expected.add(
                new Instruction(s.path("keyword").asString(), s.path("text").asString())));

        assertThat(OwnSteps.of(recorded(image), recorded(parent))).containsExactlyElementsOf(expected);
    }

    private static Resolved.Node recorded(String image) throws IOException {
        String config = Files.readString(
                Path.of("src/test/resources/fixtures/artifactory-jcr/config-" + image + ".json"));
        return new Resolved.Node("sha256:" + image, ImageConfig.parse(config).orElseThrow());
    }

    private static HistoryStep step(String createdBy, String comment) {
        return new HistoryStep(createdBy, comment, false, null);
    }

    private static Resolved.Node node(List<HistoryStep> history) {
        return new Resolved.Node("sha256:x", new ImageConfig(List.of("L1"), history, null, Map.of()));
    }
}
