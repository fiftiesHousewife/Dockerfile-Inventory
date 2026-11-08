package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

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
}
