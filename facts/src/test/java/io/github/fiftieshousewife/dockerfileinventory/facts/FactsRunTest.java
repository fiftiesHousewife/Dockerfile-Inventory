package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.assertj.core.data.Offset;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FactsRunTest {

    @TempDir
    Path discovery;

    @TempDir
    Path out;

    @BeforeEach
    void blobs() throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/good"), "FROM ubi9/ubi-minimal:9.4\nRUN microdnf install -y git\n");
        Files.writeString(discovery.resolve("blobs/bad"), "not a dockerfile\n");
    }

    @Test
    void writesFactsForEachBlobAndRecordsFailures() throws IOException {
        new FactsRun(discovery, out).run();

        assertThat(Files.readAllLines(out.resolve("facts.jsonl"))).singleElement()
                .satisfies(line -> assertThat(line).contains("\"blobSha\":\"good\"").contains("\"signatureHash\""));
        assertThat(Files.readAllLines(out.resolve("parse-failures.jsonl"))).singleElement()
                .satisfies(line -> assertThat(line).contains("\"blobSha\":\"bad\""));
    }

    @Test
    void eachFactsRowCarriesTheStepsAndBasePathLineageMatchesOn() throws IOException {
        new FactsRun(discovery, out).run();

        assertThat(Files.readAllLines(out.resolve("facts.jsonl"))).singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("\"steps\":[{\"keyword\":\"RUN\",\"text\":\"microdnf install -y git\"}]")
                        .contains("\"fromPath\":\"ubi9/ubi-minimal\""));
    }

    @Test
    void aRerunParsesNothingAlreadyRecorded() throws IOException {
        new FactsRun(discovery, out).run();

        FactsRun.Summary again = new FactsRun(discovery, out).run();

        assertThat(again).isEqualTo(new FactsRun.Summary(0, 0, 2));
        assertThat(Files.readAllLines(out.resolve("facts.jsonl"))).hasSize(1);
    }

    @Test
    void skipsBlobsDiscoveryIsStillWriting() throws IOException {
        Files.writeString(discovery.resolve("blobs/abc123.part"), "FROM ubi9\n");

        assertThat(new FactsRun(discovery, out).run().parsed()).isEqualTo(1);
    }

    @Test
    void writesTheEstateRates() throws IOException {
        Files.writeString(discovery.resolve("blobs/templated"),
                "ARG BASE\nFROM $BASE\nRUN wget -qO- https://get.example/x | bash\n");
        Files.writeString(discovery.resolve("blobs/modern"), "# syntax=docker/dockerfile:1\nFROM ubi9\n"
                + "RUN --mount=type=cache,target=/c true\nSHELL [\"pwsh\", \"-c\"]\nRUN Write-Host hi\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), """
                {"repository":"payments/api","commitSha":"c1","scope":"RELEASED","path":"Dockerfile","blobSha":"good"}
                {"repository":"platform/base","commitSha":"c2","scope":"RELEASED","path":"Dockerfile",\
                "blobSha":"templated"}
                {"repository":"platform/tools","commitSha":"c3","scope":"RELEASED","path":"build/Dockerfile",\
                "blobSha":"templated"}
                {"repository":"payments/web","commitSha":"c4","scope":"DEFAULT_BRANCH","path":"Dockerfile",\
                "blobSha":"modern"}
                """);

        new FactsRun(discovery, out).run();

        assertThat(rates()).satisfies(rates -> {
            assertThat(rates.blobs()).isEqualTo(4);
            assertThat(rates.parseFailures()).isEqualTo(1);
            assertThat(rates.parseFailureRate()).isEqualTo(0.25);
            assertThat(rates.runs()).isEqualTo(3);
            assertThat(rates.opaqueRuns()).isEqualTo(1);
            assertThat(rates.opaqueRunRate()).isCloseTo(1.0 / 3, Offset.offset(1e-9));
            assertThat(rates.unresolvedFromByOrganisation()).isEqualTo(Map.of("platform", 1));
            assertThat(rates.buildKitFeatures()).isEqualTo(Map.of("run-mount", 1, "syntax-directive", 1));
            assertThat(rates.otherShellRuns()).isEqualTo(1);
        });
    }

    @Test
    void aFileThatParsedOnlyOnTheRetryIsMarkedAndCounted() throws IOException {
        Files.writeString(discovery.resolve("blobs/spaced"), "FROM php:8.5\nARG HTTP_PROXY=\n");

        new FactsRun(discovery, out).run();

        assertThat(Files.readAllLines(out.resolve("facts.jsonl")))
                .anySatisfy(line -> assertThat(line).contains("\"blobSha\":\"spaced\"").contains("\"retried\":true"))
                .anySatisfy(line -> assertThat(line).contains("\"blobSha\":\"good\"").contains("\"retried\":false"));
        assertThat(rates().parsedOnRetry()).isEqualTo(1);
    }

    @Test
    void templatesAreCountedApartAndLeftOutOfTheParseFailureRate() throws IOException {
        Files.writeString(discovery.resolve("blobs/jinja"), "FROM {{ spec.base }}\n{% if x %}RUN y{% endif %}\n");

        new FactsRun(discovery, out).run();

        assertThat(rates()).satisfies(rates -> {
            assertThat(rates.blobs()).isEqualTo(3);
            assertThat(rates.templates()).isEqualTo(1);
            assertThat(rates.parseFailures()).isEqualTo(1);
            assertThat(rates.parseFailureRate()).isEqualTo(0.5);
        });
    }

    @Test
    void recordAppendsAParsedBlobToFactsAndAFailedOneToFailures() throws IOException {
        FactsRun run = new FactsRun(discovery, out);

        assertThat(run.record("x", "FROM ubi9\n")).isTrue();
        assertThat(run.record("y", "not a dockerfile\n")).isFalse();
        assertThat(run.recorded()).containsExactlyInAnyOrder("x", "y");
    }

    @Test
    void locationsAreEmptyWithoutAManifestAndOtherwiseRepositoryAndBlobPairs() throws IOException {
        FactsRun run = new FactsRun(discovery, out);

        assertThat(run.locations()).isEmpty();
        Files.writeString(discovery.resolve("manifest.jsonl"), "{\"repository\":\"a/b\",\"blobSha\":\"good\"}\n\n");
        assertThat(run.locations()).containsExactly(Map.entry("a/b", "good"));
    }

    @Test
    void blobsAreTheFinishedFilesInNameOrder() throws IOException {
        Files.writeString(discovery.resolve("blobs/abc123.part"), "FROM ubi9\n");
        Files.createDirectories(discovery.resolve("blobs/dir"));

        assertThat(new FactsRun(discovery, out).blobs()).extracting(blob -> String.valueOf(blob.getFileName()))
                .containsExactly("bad", "good");
    }

    @Test
    void aBlobNamedPartIsStillBeingWritten() {
        assertThat(FactsRun.stillBeingWritten(Path.of("abc.part"))).isTrue();
        assertThat(FactsRun.stillBeingWritten(Path.of("abc"))).isFalse();
    }

    @Test
    void aFactsRowWithoutStepsReadsBackWithNone() {
        FactsRun.FactRow row = JsonMapper.builder().build().readValue("{\"blobSha\":\"a\"}", FactsRun.FactRow.class);

        assertThat(row.steps()).isEmpty();
    }

    private Rates rates() {
        return JsonMapper.builder().build().readValue(out.resolve("rates.json").toFile(), Rates.class);
    }
}
