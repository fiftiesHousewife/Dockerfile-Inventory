package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FactExtractorTest {

    static ImageFacts facts(String dockerfile) {
        ParseResult parsed = new FactsParser().parse("sha", dockerfile);
        assertThat(parsed).isInstanceOf(ParseResult.Parsed.class);
        return FactExtractor.extract(((ParseResult.Parsed) parsed).file());
    }

    @Test
    void readsTheFinalBaseOfAPlainFrom() {
        ImageFacts facts = facts("FROM registry.example.com/ubi9/ubi-minimal:9.4-1227\n");

        assertThat(facts.stageCount()).isEqualTo(1);
        assertThat(facts.base()).isEqualTo(new BaseImage("registry.example.com/ubi9/ubi-minimal", "9.4-1227", null));
        assertThat(facts.base().digestPinned()).isFalse();
    }

    @Test
    void readsADigestPin() {
        ImageFacts facts = facts("FROM ubi9/ubi-minimal:9.4@sha256:" + "a".repeat(64) + "\n");

        assertThat(facts.base().digest()).isEqualTo("sha256:" + "a".repeat(64));
        assertThat(facts.base().digestPinned()).isTrue();
    }

    @Test
    void substitutesGlobalArgDefaultsIntoFrom() {
        ImageFacts facts = facts("ARG BASE=ubi9/ubi-minimal\nARG TAG=9.4\nFROM ${BASE}:${TAG}\n");

        assertThat(facts.base()).isEqualTo(new BaseImage("ubi9/ubi-minimal", "9.4", null));
        assertThat(facts.base().resolved()).isTrue();
    }

    @Test
    void leavesABaseFromAnArgWithoutDefaultUnresolved() {
        ImageFacts facts = facts("ARG BASE\nFROM $BASE\n");

        assertThat(facts.base().name()).isEqualTo("${BASE}");
        assertThat(facts.base().resolved()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "registry.example.com:5000/ubi9/ubi-minimal:9.4 | registry.example.com:5000/ubi9/ubi-minimal | 9.4",
        "registry.example.com:5000/ubi9/ubi-minimal     | registry.example.com:5000/ubi9/ubi-minimal | -",
        "ubi9/ubi-minimal:9.4                           | ubi9/ubi-minimal                           | 9.4",
    })
    void splitsATagCarriedInAnArgDefaultFromARegistryPort(String reference, String name, String tag) {
        assertThat(facts("ARG BASE=" + reference + "\nFROM ${BASE}\n").base()).isEqualTo(new BaseImage(name, tag, null));
    }

    @Test
    void followsAFinalStageBuiltFromAnEarlierStageToItsBase() {
        ImageFacts facts = facts("""
                FROM ubi9/ubi:9.4 AS base
                RUN microdnf install -y git
                FROM base AS app
                RUN echo app
                FROM app
                """);

        assertThat(facts.stageCount()).isEqualTo(3);
        assertThat(facts.base()).isEqualTo(new BaseImage("ubi9/ubi", "9.4", null));
    }

    @Test
    void collectsPackageCallsPerStageFromShellExecAndHeredocRuns() {
        ImageFacts facts = facts("""
                FROM ubi9/ubi AS build
                RUN microdnf install -y gcc && microdnf clean all
                FROM ubi9/ubi-minimal
                RUN ["microdnf", "install", "-y", "tar"]
                RUN <<EOF
                microdnf install -y git
                microdnf clean all
                EOF
                """);

        PackageCall clean = new PackageCall("microdnf", "clean", List.of(), List.of(), false);
        assertThat(facts.packageCalls()).containsExactly(
                new StagedCall(0, new PackageCall("microdnf", "install", List.of("-y"), List.of("gcc"), true)),
                new StagedCall(0, clean),
                new StagedCall(1, new PackageCall("microdnf", "install", List.of("-y"), List.of("tar"), false)),
                new StagedCall(1, new PackageCall("microdnf", "install", List.of("-y"), List.of("git"), true)),
                new StagedCall(1, clean));
    }

    @Test
    void countsRunsAndTheOnesTheTokeniserDeclined() {
        ImageFacts facts = facts("""
                FROM ubi9/ubi AS build
                RUN if [ -f x ]; then make; fi
                FROM ubi9/ubi-minimal
                RUN microdnf install -y tar
                RUN curl -sSL https://x.example/install | sh
                """);

        assertThat(facts.runs()).isEqualTo(3);
        assertThat(facts.opaqueRuns()).isEqualTo(2);
    }
}
