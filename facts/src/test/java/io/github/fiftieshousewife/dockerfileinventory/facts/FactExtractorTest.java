package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

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
}
