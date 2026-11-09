package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogueMainTest {

    @TempDir
    Path out;

    @Test
    void writesTheCatalogueUnderTheOutputDirectoryAndNoPage() {
        CatalogueMain.run(Map.of("FACTS_DIR", EstateTest.FIXTURES.toString(), "DISCOVERY_DIR",
                EstateTest.FIXTURES.toString(), "OUT_DIR", out.toString()));

        assertAll(
                () -> assertThat(out.resolve(CatalogueRun.PATH)).exists(),
                () -> assertThat(out.resolve("index.html")).doesNotExist());
    }

    @Test
    void aMissingSettingIsRefusedByName() {
        assertThatIllegalStateException().isThrownBy(() -> CatalogueMain.run(Map.of("OUT_DIR", out.toString())))
                .withMessage("FACTS_DIR is not set");
    }
}
