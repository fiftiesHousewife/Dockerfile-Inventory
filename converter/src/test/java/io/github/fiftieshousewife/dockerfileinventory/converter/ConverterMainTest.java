package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConverterMainTest {

    @Test
    void anOptionalSettingThatIsUnsetOrBlankIsNoPath() {
        assertThat(ConverterMain.optional(null)).isNull();
        assertThat(ConverterMain.optional("  ")).isNull();
        assertThat(ConverterMain.optional("lists/opt-in.txt")).isEqualTo(Path.of("lists/opt-in.txt"));
    }

    @Test
    void aRequiredSettingThatIsUnsetOrBlankStopsTheRunNamingIt() {
        assertThat(ConverterMain.required("OUT_DIR", "out")).isEqualTo("out");
        assertThatThrownBy(() -> ConverterMain.required("OUT_DIR", " ")).hasMessage("OUT_DIR is not set");
        assertThatThrownBy(() -> ConverterMain.required("OUT_DIR", null)).hasMessage("OUT_DIR is not set");
    }

    @Test
    void theJavaRoundTripIsOnUnlessSetToFalseAndAnythingElseIsRefusedByName() {
        assertAll(
                () -> assertThat(ConverterMain.javaRoundTrip(null)).isTrue(),
                () -> assertThat(ConverterMain.javaRoundTrip("true")).isTrue(),
                () -> assertThat(ConverterMain.javaRoundTrip("false")).isFalse(),
                () -> assertThatIllegalStateException().isThrownBy(() -> ConverterMain.javaRoundTrip("no"))
                        .withMessageContaining("JAVA_ROUND_TRIP"));
    }

    @Test
    void aRunConvertsTheDiscoveryItIsGivenIntoItsOutputFolder(@TempDir Path discovery, @TempDir Path out)
            throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/a"), "FROM ubi9/ubi-minimal:9.4\nUSER 1001\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), "{\"repository\":\"team/app\",\"path\":\"Dockerfile\","
                + "\"blobSha\":\"a\"}\n");

        ConverterMain.run(Map.of("DISCOVERY_DIR", discovery.toString(), "OUT_DIR", out.toString(),
                "JAVA_ROUND_TRIP", "false"));

        assertThat(out.resolve("roundtrip.jsonl")).isNotEmptyFile();
    }
}
