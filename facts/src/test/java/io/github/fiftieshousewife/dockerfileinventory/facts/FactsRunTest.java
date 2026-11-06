package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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

        assertThat(again.parsed()).isZero();
        assertThat(again.failed()).isZero();
        assertThat(again.alreadyDone()).isEqualTo(2);
        assertThat(Files.readAllLines(out.resolve("facts.jsonl"))).hasSize(1);
    }

    @Test
    void skipsBlobsDiscoveryIsStillWriting() throws IOException {
        Files.writeString(discovery.resolve("blobs/abc123.part"), "FROM ubi9\n");

        assertThat(new FactsRun(discovery, out).run().parsed()).isEqualTo(1);
    }

    @Test
    void writesTheEstateRates() throws IOException {
        Files.writeString(discovery.resolve("blobs/templated"), "ARG BASE\nFROM $BASE\nRUN if [ -f x ]; then y; fi\n");
        Files.writeString(discovery.resolve("blobs/modern"), "# syntax=docker/dockerfile:1\nFROM ubi9\nRUN --mount=type=cache,target=/c true\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), """
                {"repository":"payments/api","commitSha":"c1","scope":"RELEASED","path":"Dockerfile","blobSha":"good"}
                {"repository":"platform/base","commitSha":"c2","scope":"RELEASED","path":"Dockerfile","blobSha":"templated"}
                {"repository":"platform/tools","commitSha":"c3","scope":"RELEASED","path":"build/Dockerfile","blobSha":"templated"}
                {"repository":"payments/web","commitSha":"c4","scope":"DEFAULT_BRANCH","path":"Dockerfile","blobSha":"modern"}
                """);

        new FactsRun(discovery, out).run();

        Rates rates = JsonMapper.builder().build().readValue(out.resolve("rates.json").toFile(), Rates.class);
        assertThat(rates.blobs()).isEqualTo(4);
        assertThat(rates.parseFailures()).isEqualTo(1);
        assertThat(rates.parseFailureRate()).isEqualTo(0.25);
        assertThat(rates.runs()).isEqualTo(3);
        assertThat(rates.opaqueRuns()).isEqualTo(1);
        assertThat(rates.opaqueRunRate()).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(rates.unresolvedFromByOrganisation()).isEqualTo(Map.of("platform", 1));
        assertThat(rates.buildKitFeatures()).isEqualTo(Map.of("run-mount", 1, "syntax-directive", 1));
    }
}
