package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineageFilesTest {

    @TempDir
    Path dir;

    @Test
    void recordedTagRowsReadBackInOrder() {
        LineageFiles files = new LineageFiles(dir.resolve("out"));
        TagRow placed = new TagRow("docker-local", "team/app", "1", Instant.parse("2026-10-01T00:00:00Z"), "c1",
                null, null);
        TagRow skipped = new TagRow("docker-local", "team/arm", "1", null, null, Resolved.Reason.NO_AMD64, "x");

        files.record(placed);
        files.record(skipped);

        assertThat(files.tags()).containsExactly(placed, skipped);
    }

    @Test
    void aRowRecordedBeforeSizesAndArchitecturesReadsWithNeither() throws IOException {
        Files.createDirectories(dir.resolve("old"));
        Files.writeString(dir.resolve("old/images.jsonl"), "{\"repository\":\"docker-local\",\"image\":\"team/app\","
                + "\"tag\":\"1\",\"lastPushed\":null,\"configDigest\":\"c1\",\"skipped\":null,\"skipDetail\":null}\n");

        assertThat(new LineageFiles(dir.resolve("old")).tags()).containsExactly(
                new TagRow("docker-local", "team/app", "1", null, "c1", null, null));
    }

    @Test
    void aStoredConfigIsFoundByItsDigestAndAnUnstoredOneIsNot() {
        LineageFiles files = new LineageFiles(dir);

        files.storeConfig("sha256:abc", "{}");

        assertAll(
                () -> assertThat(files.hasConfig("sha256:abc")).isTrue(),
                () -> assertThat(files.config("sha256:abc")).contains("{}"),
                () -> assertThat(files.config("sha256:def")).isEmpty(),
                () -> assertThat(dir.resolve("configs/sha256_abc.json")).exists());
    }

    @Test
    void writeReplacesAWholeFile() throws IOException {
        LineageFiles files = new LineageFiles(dir);

        files.write("links.jsonl", List.of("a", "b"));
        files.write("links.jsonl", List.of("c"));

        assertThat(Files.readAllLines(dir.resolve("links.jsonl"))).containsExactly("c");
    }

    @Test
    void linesSkipBlankLinesAndAMissingFileHasNone() throws IOException {
        Path file = dir.resolve("rows.jsonl");
        Files.writeString(file, "one\n\n  \ntwo\n");

        assertAll(
                () -> assertThat(LineageFiles.lines(file)).containsExactly("one", "two"),
                () -> assertThat(LineageFiles.lines(dir.resolve("missing.jsonl"))).isEmpty());
    }

    @Test
    void nothingCanBeWrittenUnderARegularFile() throws IOException {
        LineageFiles files = new LineageFiles(Files.writeString(dir.resolve("file"), "x"));
        TagRow row = new TagRow("docker-local", "team/app", "1", null, null, Resolved.Reason.GONE, "x");

        assertAll(() -> assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> files.record(row)),
                () -> assertThatExceptionOfType(UncheckedIOException.class)
                        .isThrownBy(() -> files.write("lineage.jsonl", List.of())),
                () -> assertThatExceptionOfType(UncheckedIOException.class)
                        .isThrownBy(() -> files.storeConfig("sha256:a", "{}")));
    }

    @Test
    void aConfigOrListingThatIsAFolderCannotBeRead() throws IOException {
        Files.createDirectories(dir.resolve("configs/sha256_a.json"));
        LineageFiles files = new LineageFiles(dir);

        assertAll(() -> assertThatExceptionOfType(UncheckedIOException.class)
                        .isThrownBy(() -> files.config("sha256:a")),
                () -> assertThatExceptionOfType(UncheckedIOException.class)
                        .isThrownBy(() -> LineageFiles.lines(dir)));
    }
}
