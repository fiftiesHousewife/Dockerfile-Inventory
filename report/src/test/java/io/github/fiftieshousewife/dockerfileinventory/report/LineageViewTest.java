package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.ImageRow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineageViewTest {

    static final Path CHAIN = Path.of("src/test/resources/fixtures/lineage-chain");

    static final Map<String, List<Location>> LOCATIONS = Map.of(
            "1723284a866651acfdadf451286d13c452181e12",
            List.of(new Location("sclorg/s2i-base-container", "core/Dockerfile.c9s", "DEFAULT_BRANCH")),
            "84d379564c82cd28d8d358b5f94373bb49f0fb70",
            List.of(new Location("sclorg/s2i-base-container", "base/Dockerfile.c9s", "DEFAULT_BRANCH")),
            "22461d2436a54f83f08d9410d05897f7562a6c23",
            List.of(new Location("sclorg/s2i-python-container", "3.12/Dockerfile.c9s", "DEFAULT_BRANCH")),
            "6070555cb3c17f02425e461698624ec091d9ba5b",
            List.of(new Location("sclorg/nginx-container", "1.20/Dockerfile.rhel8", "DEFAULT_BRANCH")),
            "6add3832a59ef3bd8252cf3b20477d6eba46a8fa",
            List.of(new Location("sclorg/nginx-container", "1.24/Dockerfile.c9s", "DEFAULT_BRANCH")),
            "b2c598034275558b6409ad39c8b92c6651b25b2e",
            List.of(new Location("sclorg/nginx-container", "1.22/Dockerfile.c9s", "DEFAULT_BRANCH")),
            "d69ccd7230a80986bdb0df3c9ac42814871de9e1",
            List.of(new Location("sclorg/nginx-container", "1.24/Dockerfile.c8s", "DEFAULT_BRANCH")));

    @Test
    void eachImageIsNamedWithItsParentItsTierAndItsDockerfilesLocations() {
        assertThat(LineageView.read(CHAIN, LOCATIONS).images()).contains(
                new ImageRow("docker-local/centos/centos:stream9", null, "NONE", List.of()),
                new ImageRow("docker-local/sclorg/s2i-core-c9s:c9s", "docker-local/centos/centos:stream9",
                        "PROBABLE", List.of("sclorg/s2i-base-container: core/Dockerfile.c9s")),
                new ImageRow("docker-local/sclorg/s2i-base-c9s:c9s", "docker-local/sclorg/s2i-core-c9s:c9s",
                        "PROBABLE", List.of("sclorg/s2i-base-container: base/Dockerfile.c9s")),
                new ImageRow("docker-local/sclorg/s2i-python-container-3-12-c9s:c9s",
                        "docker-local/sclorg/s2i-base-c9s:c9s", "PROBABLE",
                        List.of("sclorg/s2i-python-container: 3.12/Dockerfile.c9s")));
    }

    @Test
    void theCountsComeFromLineagesRates() {
        assertThat(LineageView.read(CHAIN, LOCATIONS).counts()).isEqualTo(
                new LineageView.Counts(5, 1.0, Map.of("PROBABLE", 4, "NONE", 1), 0, 1, 0));
    }

    @Test
    void basesAreTheImagesOthersAreBuiltOnMostUsedFirst() {
        assertThat(LineageView.read(CHAIN, LOCATIONS).bases()).containsExactly(
                new LineageView.BaseUse("docker-local/sclorg/s2i-core-c9s:c9s", 2),
                new LineageView.BaseUse("docker-local/centos/centos:stream9", 1),
                new LineageView.BaseUse("docker-local/sclorg/s2i-base-c9s:c9s", 1));
    }

    @Test
    void gapsAndOutdatedBasesAreNamedNewestFirst(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("images.jsonl"), """
                {"repository":"r","image":"base","tag":"1","lastPushed":"2026-01-01T00:00:00Z","configDigest":"b1"}
                {"repository":"r","image":"base","tag":"2","lastPushed":"2026-06-01T00:00:00Z","configDigest":"b2"}
                {"repository":"r","image":"app","tag":"1","lastPushed":"2026-02-01T00:00:00Z","configDigest":"a1"}
                {"repository":"r","image":"tool","tag":"9","lastPushed":"2026-03-01T00:00:00Z","configDigest":"t9"}
                """);
        Files.writeString(dir.resolve("gaps.jsonl"), """
                {"configDigest":"a1","references":[],"lastPushed":"2026-02-01T00:00:00Z"}
                {"configDigest":"t9","references":[],"lastPushed":"2026-03-01T00:00:00Z"}
                """);
        Files.writeString(dir.resolve("outdated-bases.jsonl"), """
                {"image":"a1","parent":"b1","imageName":"r/base","newer":"b2"}
                """);

        assertThat(LineageView.read(dir, Map.of())).satisfies(view -> {
            assertThat(view.gaps()).containsExactly("r/tool:9", "r/app:1");
            assertThat(view.outdated()).containsExactly(new LineageView.OutdatedRow("r/app:1", "r/base:1", "r/base:2"));
        });
    }
}
