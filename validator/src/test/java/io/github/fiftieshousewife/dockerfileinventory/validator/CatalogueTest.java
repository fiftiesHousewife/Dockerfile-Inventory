package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogueTest {

    @Test
    void readsEachDockerfilesArchetypeItsRepresentativeAndTheDockerfilesBuiltOnIt(@TempDir Path dir)
            throws IOException {
        Files.writeString(dir.resolve("archetypes.jsonl"), """
                {"rank":1,"key":"k1","blobShas":["a","b"],"representative":"b"}
                """);
        Files.createDirectories(dir.resolve("catalogue"));
        Files.writeString(dir.resolve("catalogue/dockerfiles.jsonl"), """
                {"blobSha":"a","links":{"parents":[],"children":[{"blobSha":"c","source":"inferred"}]}}
                {"blobSha":"c","links":{"parents":[{"blobSha":"a"}],"children":[]}}
                {"blobSha":"u","links":null}
                """);

        Catalogue catalogue = Catalogue.read(dir);

        assertAll(
                () -> assertThat(catalogue.archetype("a")).isEqualTo("k1"),
                () -> assertThat(catalogue.archetype("c")).isEqualTo("c"),
                () -> assertThat(catalogue.representative("k1")).isEqualTo("b"),
                () -> assertThat(catalogue.children("a")).isEqualTo(List.of("c")),
                () -> assertThat(catalogue.children("u")).isEmpty());
    }

    @Test
    void theRankingsGroupsOfUnparseableFilesAndTemplatesAreNotArchetypes(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("archetypes.jsonl"), """
                {"rank":1,"key":"parse-failures","blobShas":["a","b"],"representative":"a"}
                {"rank":2,"key":"templates","blobShas":["c","d"],"representative":"c"}
                """);
        Files.createDirectories(dir.resolve("catalogue"));
        Files.writeString(dir.resolve("catalogue/dockerfiles.jsonl"), "");

        Catalogue catalogue = Catalogue.read(dir);

        assertAll(
                () -> assertThat(catalogue.archetype("a")).isEqualTo("a"),
                () -> assertThat(catalogue.archetype("c")).isEqualTo("c"));
    }
}
