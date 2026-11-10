package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesMainTest {

    @Test
    void anOptionalSettingThatIsUnsetOrBlankIsNoPath() {
        assertAll(
                () -> assertThat(RulesMain.optional(null)).isNull(),
                () -> assertThat(RulesMain.optional(" ")).isNull(),
                () -> assertThat(RulesMain.optional("out/lineage")).isEqualTo(Path.of("out/lineage")));
    }

    @Test
    void aRequiredSettingThatIsUnsetOrBlankStopsTheRunNamingIt() {
        assertAll(
                () -> assertThat(RulesMain.required("OUT_DIR", "out")).isEqualTo("out"),
                () -> assertThatThrownBy(() -> RulesMain.required("OUT_DIR", null)).hasMessage("OUT_DIR is not set"),
                () -> assertThatThrownBy(() -> RulesMain.required("OUT_DIR", "")).hasMessage("OUT_DIR is not set"));
    }

    @Test
    void theSettingsGivenAreTheOnesReadAndAMissingOneIsRefusedByName() {
        assertThatIllegalStateException().isThrownBy(() -> RulesMain.run(Map.of("OUT_DIR", "/o")))
                .withMessage("DISCOVERY_DIR is not set");
    }

    @Test
    void theCatalogueDirectoryRunsTheRulesOverTheWholeEstate(@TempDir Path dir) throws IOException {
        Path discovery = Files.createDirectories(dir.resolve("discovery"));
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/app"), "FROM ubi9\nCMD [\"/app\"]\n");
        Files.writeString(discovery.resolve("manifest.jsonl"),
                "{\"repository\":\"team/app\",\"path\":\"Dockerfile\",\"blobSha\":\"app\"}\n");
        Path catalogue = Files.createDirectories(dir.resolve("catalogue/catalogue"));
        Files.writeString(catalogue.resolve("dockerfiles.jsonl"),
                pinned("app", "1") + pinned("other", "2"));

        RulesMain.run(Map.of("DISCOVERY_DIR", discovery.toString(),
                "CATALOGUE_DIR", dir.resolve("catalogue").toString(), "OUT_DIR", dir.resolve("out").toString()));

        assertThat(dir.resolve("out/findings.jsonl")).content().contains("\"rule\":\"R4\"");
    }

    private static String pinned(String sha, String digest) {
        return "{\"blobSha\":\"" + sha + "\",\"build\":{\"stages\":[{\"baseName\":\"ubi9\",\"baseTag\":\"9\","
                + "\"baseDigest\":\"sha256:" + digest.repeat(64) + "\"}]}}\n";
    }
}
