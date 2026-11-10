package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonTest {

    @Test
    void aValueIsWrittenByteForByteAsJqWroteTheBanksCases() throws IOException {
        Path written = Path.of("../testdata/corpus/images/okd-configmap-reloader-ed17ad1e/config.json");

        assertThat(Json.pretty(Json.read(written))).isEqualTo(Files.readString(written));
    }

    @Test
    void aFilesRowsAreItsLinesThatAreNotBlank(@TempDir Path folder) throws IOException {
        Path file = Files.writeString(folder.resolve("rows.jsonl"), "{\"a\":1}\n\n{\"a\":2}\n");

        assertThat(Json.rows(file).map(row -> row.path("a").asInt())).containsExactly(1, 2);
    }

    @Test
    void aMissingFileOfRowsIsAnInputOutputFailure(@TempDir Path folder) {
        assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> Json.rows(folder.resolve("absent")));
    }

    @Test
    void emptyArraysAndObjectsAreWrittenWithoutASpace() {
        assertThat(Json.pretty(Json.MAPPER.readTree("{\"a\":[],\"b\":{}}")))
                .isEqualTo("{\n  \"a\": [],\n  \"b\": {}\n}\n");
    }
}
