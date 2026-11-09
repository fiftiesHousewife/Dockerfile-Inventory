package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonLinesTest {

    @TempDir
    Path dir;

    @Test
    void eachLineIsOneRowAndBlankLinesAreLeftOut() throws IOException {
        Path file = Files.writeString(dir.resolve("rows.jsonl"), "{\"a\":1}\n\n{\"a\":2}\n");

        assertThat(JsonLines.read(file)).extracting(row -> row.path("a").asInt()).containsExactly(1, 2);
    }

    @Test
    void aMissingFileIsRefused() {
        assertThatThrownBy(() -> JsonLines.read(dir.resolve("missing.jsonl")))
                .isInstanceOf(UncheckedIOException.class);
    }
}
