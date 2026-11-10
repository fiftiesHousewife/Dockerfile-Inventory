package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ImageConfigTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void textThatIsNotJsonReadsAsNothing() {
        assertAll(
                () -> assertThat(ImageConfig.json("{")).isEmpty(),
                () -> assertThat(ImageConfig.json("{\"a\": 1}")).hasValueSatisfying(node -> assertThat(node.has("a"))
                        .isTrue()));
    }

    @Test
    void anEnvEntryWithoutAnEqualsSignHasAnEmptyValue() {
        assertThat(ImageConfig.env(JSON.readTree("[\"A=1=2\", \"BARE\"]")))
                .containsExactly(Map.entry("A", "1=2"), Map.entry("BARE", ""));
    }

    @Test
    void historyEntriesKeepTheirFieldsAndMissingOnesAreNull() {
        assertThat(ImageConfig.history(JSON.readTree(
                "[{\"created_by\": \"make\", \"empty_layer\": true, \"created\": \"2026\"}, {}]")))
                .containsExactly(new HistoryStep("make", null, true, "2026"), new HistoryStep(null, null, false, null));
    }

    @Test
    void ofReadsAConfigWithoutAConfigSection() {
        assertAll(
                () -> assertThat(ImageConfig.of(JSON.readTree("{\"rootfs\": {\"diff_ids\": [\"L1\"]}}")).diffIds())
                        .containsExactly("L1"),
                () -> assertThat(ImageConfig.of(JSON.readTree("{\"rootfs\": {\"diff_ids\": [\"L1\"]}}")).user())
                        .isNull(),
                () -> assertThat(ImageConfig.of(JSON.readTree("{\"rootfs\": {\"diff_ids\": [\"L1\"]}}")).env())
                        .isEmpty());
    }

    @Test
    void readsDiffIdsUserAndLabels() {
        assertThat(ImageConfig.parse(Fixtures.artifactory("config.json"))).hasValueSatisfying(config -> {
            assertThat(config.diffIds()).containsExactly("sha256:" + "3".repeat(64), "sha256:" + "4".repeat(64));
            assertThat(config.user()).isEqualTo("1001");
            assertThat(config.labels()).isEqualTo(Map.of("name", "team/app"));
        });
    }

    @Test
    void aMissingUserAndLabelsGiveNone() {
        assertThat(ImageConfig.parse(Fixtures.artifactory("config-bare.json"))).hasValueSatisfying(config -> {
            assertThat(config.user()).isNull();
            assertThat(config.labels()).isEmpty();
        });
    }

    @Test
    void keepsHistoryStepsInOrderWithEmptyLayerFalseWhenAbsent() {
        assertThat(ImageConfig.parse(Fixtures.artifactory("config-history.json")).orElseThrow().history())
                .extracting(HistoryStep::createdBy, HistoryStep::comment, HistoryStep::emptyLayer)
                .containsExactly(
                        tuple("/bin/sh -c #(nop) ADD file:base in / ", null, false),
                        tuple("/bin/sh -c #(nop) LABEL name=base", null, true),
                        tuple("/bin/sh -c microdnf install -y git", "FROM base:1", false),
                        tuple("/bin/sh -c #(nop) USER 1001", null, true));
    }

    @Test
    void textThatIsNotJsonOrHasNoRootfsIsUnreadable() {
        assertAll(
                () -> assertThat(ImageConfig.parse("not json")).isEmpty(),
                () -> assertThat(ImageConfig.parse("{\"config\": {}}")).isEmpty());
    }

    @Test
    void readsEnvWorkingDirEntrypointCmdAndOnBuild() {
        assertAll(
                () -> assertThat(ImageConfig.parse(Fixtures.artifactory("config-full.json")))
                        .hasValueSatisfying(config -> {
                    assertThat(config.env()).containsExactly(
                            Map.entry("PATH", "/opt/app-root/bin:/usr/bin"), Map.entry("APP_ROOT", "/opt/app-root"),
                            Map.entry("EMPTY", ""));
                    assertThat(config.workingDir()).isEqualTo("/opt/app-root/src");
                    assertThat(config.entrypoint()).containsExactly("container-entrypoint");
                    assertThat(config.cmd()).containsExactly("/bin/sh", "-c", "$STI_SCRIPTS_PATH/usage");
                    assertThat(config.onBuild()).containsExactly("RUN make");
                }),
                () -> assertThat(ImageConfig.parse(Fixtures.artifactory("config-bare.json")))
                        .hasValueSatisfying(config -> {
                    assertThat(config.workingDir()).isNull();
                    assertThat(config.entrypoint()).isEmpty();
                    assertThat(config.onBuild()).isEmpty();
                }));
    }
}
