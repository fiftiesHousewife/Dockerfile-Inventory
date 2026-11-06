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

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "FROM ubi9                                    | -    | ROOT",
        "FROM ubi9\\nUSER root                        | root | ROOT",
        "FROM ubi9\\nUSER 0:0                         | 0    | ROOT",
        "FROM ubi9\\nUSER 1001                        | 1001 | NUMERIC",
        "FROM ubi9\\nUSER 1001:0                      | 1001 | NUMERIC",
        "FROM ubi9\\nUSER appuser                     | appuser | NAMED",
        "FROM ubi9\\nARG APP_UID=1001\\nUSER ${APP_UID} | 1001 | NUMERIC",
        "FROM ubi9\\nENV APP_UID=1002\\nUSER $APP_UID   | 1002 | NUMERIC",
        "FROM ubi9\\nARG APP_UID\\nUSER $APP_UID       | ${APP_UID} | UNRESOLVED",
        "FROM ubi9\\nUSER 1001\\nUSER appuser         | appuser | NAMED",
        "FROM ubi9 AS base\\nUSER 1001\\nFROM base     | 1001 | NUMERIC",
        "FROM ubi9 AS build\\nUSER 1001\\nFROM ubi9    | -    | ROOT",
    })
    void readsTheFinalUserAndItsKind(String dockerfile, String user, UserKind kind) {
        ImageFacts facts = facts(dockerfile.replace("\\n", "\n") + "\n");

        assertThat(facts.finalUser()).isEqualTo(user);
        assertThat(facts.userKind()).isEqualTo(kind);
    }

    @Test
    void readsExposedPortsSortedWithoutDuplicates() {
        ImageFacts facts = facts("""
                FROM ubi9 AS base
                EXPOSE 9090
                FROM base
                EXPOSE 8443/tcp 8080 53/udp
                EXPOSE 8080/tcp
                """);

        assertThat(facts.exposedPorts()).containsExactly("53/udp", "8080", "8443", "9090");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "FROM ubi9                                                          | NONE",
        "FROM ubi9\\nENTRYPOINT [\"java\", \"-jar\", \"/opt/app.jar\"]          | EXEC",
        "FROM ubi9\\nENTRYPOINT java -jar /opt/app.jar                      | SHELL",
        "FROM ubi9\\nENTRYPOINT java -jar /a.jar\\nENTRYPOINT [\"/run.sh\"] | EXEC",
        "FROM ubi9 AS base\\nENTRYPOINT [\"/run.sh\"]\\nFROM base          | EXEC",
    })
    void readsTheEntrypointForm(String dockerfile, EntrypointForm form) {
        assertThat(facts(dockerfile.replace("\\n", "\n") + "\n").entrypointForm()).isEqualTo(form);
    }

    @Test
    void notesBuildKitFeaturesInUse() {
        ImageFacts facts = facts("""
                # syntax=docker/dockerfile:1.7
                FROM ubi9
                RUN --mount=type=cache,target=/var/cache/dnf dnf install -y git
                RUN <<EOF
                echo hi
                EOF
                """);

        assertThat(facts.buildKitFeatures()).containsExactly("heredoc", "run-mount", "syntax-directive");
    }

    @Test
    void ignoresASyntaxLineAfterAnOrdinaryComment() {
        assertThat(facts("# base image for payments\n# syntax=docker/dockerfile:1\nFROM ubi9\n").buildKitFeatures()).isEmpty();
    }

    @Test
    void aSyntaxLineAfterAnInstructionIsReadAsTheCommentDockerReadsItAs() {
        assertThat(facts("FROM ubi9\n# syntax=docker/dockerfile:1\nRUN echo\n").buildKitFeatures()).isEmpty();
    }
}
