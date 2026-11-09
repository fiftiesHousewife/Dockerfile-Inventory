package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void aValueIsWrittenByteForByteAsJqWroteTheBanksCases() throws IOException {
        Path written = Path.of("../testdata/corpus/images/okd-configmap-reloader-ed17ad1e/config.json");

        assertThat(Json.pretty(Json.read(written))).isEqualTo(Files.readString(written));
    }

    @Test
    void emptyArraysAndObjectsAreWrittenWithoutASpace() {
        assertThat(Json.pretty(Json.MAPPER.readTree("{\"a\":[],\"b\":{}}")))
                .isEqualTo("{\n  \"a\": [],\n  \"b\": {}\n}\n");
    }
}
