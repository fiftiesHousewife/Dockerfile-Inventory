package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FactsParserTest {

    @Test
    void parsesAMinimalDockerfileToOneStage() {
        ParseResult result = new FactsParser().parse("abc", "FROM ubi9/ubi-minimal\n");

        assertThat(result).isInstanceOfSatisfying(ParseResult.Parsed.class,
                parsed -> assertThat(parsed.file().getStages()).hasSize(1));
    }

    @Test
    void recordsTextTheGrammarRejectsAsAFailure() {
        ParseResult result = new FactsParser().parse("bad", "this is not a dockerfile\n");

        assertThat(result).isInstanceOfSatisfying(ParseResult.Failed.class, failed -> {
            assertThat(failed.blobSha()).isEqualTo("bad");
            assertThat(failed.message()).isNotBlank();
        });
    }

    @Test
    void recordsALineTheParserGluedOntoThePreviousInstructionAsAFailure() {
        ParseResult result = new FactsParser().parse("typo", "FROM ubi9\nRUNN echo typo\n");

        assertThat(result).isInstanceOfSatisfying(ParseResult.Failed.class,
                failed -> assertThat(failed.message()).contains("RUNN echo typo"));
    }

    @Test
    void theMergedLineMessageNamesTheFirstNonBlankLine() {
        ParseResult result = new FactsParser().parse("k8s", "FROM BASEIMAGE\n\nCROSS_BUILD_COPY qemu /usr/bin/\n");

        assertThat(result).isInstanceOfSatisfying(ParseResult.Failed.class,
                failed -> assertThat(failed.message()).endsWith(": CROSS_BUILD_COPY qemu /usr/bin/"));
    }

    @Test
    void acceptsShellContinuationsInsideARun() {
        assertThat(new FactsParser().parse("ok", "FROM ubi9\nRUN echo a \\\n  && echo b\n"))
                .isInstanceOf(ParseResult.Parsed.class);
    }

    @Test
    void acceptsACommentLineInsideAContinuedRun() {
        assertThat(new FactsParser().parse("ok", """
                FROM ubi9
                RUN rpm -V git && \\
                    # Remove redhat-logos-httpd to keep the image small.
                    rpm -e --nodeps redhat-logos-httpd && \\
                    yum -y clean all
                """)).isInstanceOf(ParseResult.Parsed.class);
    }

    @Test
    void acceptsAHeredocRun() {
        assertThat(new FactsParser().parse("ok", "FROM ubi9\nRUN <<EOF\nmicrodnf install -y git\nmicrodnf clean all\nEOF\n"))
                .isInstanceOf(ParseResult.Parsed.class);
    }

    @Test
    void theRealS2iPythonLinesWithAnApostropheInACommentInsideARunParse() throws IOException {
        String text = fixture("s2i-python-3.12-comments.Dockerfile");

        assertThat(new FactsParser().parse("py", text)).isInstanceOfSatisfying(ParseResult.Parsed.class,
                parsed -> assertThat(Steps.of(parsed.file()).getLast().text())
                        .startsWith("python3.12 -m venv ${APP_ROOT} && ${APP_ROOT}/bin/pip install"));
    }

    @Test
    void theRealNginxLinesWithAKeyValueCommentAfterAnInstructionParse() throws IOException {
        assertThat(new FactsParser().parse("nginx", fixture("nginx-1.20-comments.Dockerfile")))
                .isInstanceOf(ParseResult.Parsed.class);
    }

    @Test
    void blankingCommentsKeepsLineNumbersParserDirectivesAndHeredocComments() {
        String text = """
                # syntax=docker/dockerfile:1
                FROM ubi9
                # it's here \\
                RUN make && \\
                # it's inside a continuation \\
                    make install
                RUN <<EOT
                # kept: part of the script
                echo hi
                EOT
                """;

        assertThat(RetryText.withoutComments(text)).isEqualTo("""
                # syntax=docker/dockerfile:1
                FROM ubi9

                RUN make && \\
                \\
                    make install
                RUN <<EOT
                # kept: part of the script
                echo hi
                EOT
                """);
    }

    @Test
    void aFileThatParsesFirstTimeKeepsItsComments() {
        String text = "FROM ubi9\n# a comment the converter carries\nRUN make\n";

        assertThat(((ParseResult.Parsed) new FactsParser().parse("ok", text)).file().printAll()).isEqualTo(text);
    }

    @Test
    void aFileThatFailsAndHoldsTemplateMarkersIsATemplateWithTheParsersOwnMessage() throws IOException {
        ParseResult result = new FactsParser().parse("tpl", fixture("s2i-nodejs-jinja-template.Dockerfile"));

        assertThat(result).isInstanceOfSatisfying(ParseResult.Failed.class, failed -> assertThat(failed.message())
                .startsWith(FactsParser.TEMPLATE)
                .contains("{{ spec.s2i_base }}")
                .doesNotContain("Could not initialize"));
    }

    @Test
    void aFileThatParsesButHoldsJinjaMarkersIsATemplate() throws IOException {
        ParseResult result = new FactsParser().parse("tpl", fixture("mariadb-jinja-template-that-parses.Dockerfile"));

        assertThat(result).isInstanceOfSatisfying(ParseResult.Failed.class, failed -> assertThat(failed.message())
                .startsWith(FactsParser.TEMPLATE).contains("{{ spec.s2i_base }}"));
    }

    @Test
    void aJinjaBlockTagMakesAParsingFileATemplate() {
        assertThat(new FactsParser().parse("tpl", "FROM ubi9\nLABEL a=\"{% if x %}y{% endif %}\"\n"))
                .isInstanceOf(ParseResult.Failed.class);
    }

    @Test
    void aGoTemplateInAValidFileIsNotATemplate() {
        assertThat(new FactsParser().parse("ok", """
                FROM ubi9
                RUN docker inspect --format '{{.Id}}' x && docker inspect --format '{{ .Config.User }}' x \\
                    && docker inspect --format '{{ index .Config.Labels "name" }}' x
                """)).isInstanceOf(ParseResult.Parsed.class);
    }

    @Test
    void theRealPostgresqlLinesWithALoneBackslashLineAfterACopyParse() throws IOException {
        assertThat(new FactsParser().parse("pg", fixture("postgresql-13-lone-backslash.Dockerfile")))
                .isInstanceOfSatisfying(ParseResult.Parsed.class, parsed -> assertThat(Steps.of(parsed.file()))
                        .extracting(Step::keyword).containsExactly("COPY", "RUN"));
    }

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/fixtures/dockerfiles", name));
    }

    @Test
    void anExecFormWithASpaceBeforeACommaParsesAndKeepsItsWords() {
        String dockerfile = "FROM php:8.5\nCMD [\"php\", \"-S\" , \"0.0.0.0:8080\"]\n";

        assertThat(new FactsParser().parse("php", dockerfile)).isInstanceOf(ParseResult.Parsed.class);
        assertThat(Steps.of(((ParseResult.Parsed) new FactsParser().parse("php", dockerfile)).file()))
                .contains(new Step("CMD", "[\"php\", \"-S\", \"0.0.0.0:8080\"]"));
    }

    @Test
    void aFlagWhoseValueIsAVariableWithADefaultParses() {
        String dockerfile = "FROM --platform=${BUILDPLATFORM:-linux/amd64} docker.io/library/rust:latest AS builder\n"
                + "COPY --chown=${UID:-1001} a /b\n";

        assertThat(new FactsParser().parse("rust", dockerfile)).isInstanceOf(ParseResult.Parsed.class);
        assertThat(FactExtractorTest.facts(dockerfile).base().name()).isEqualTo("docker.io/library/rust");
    }

    @Test
    void anArgDeclaringSeveralNamesWithoutValuesParsesAndLeavesEachUnknown() {
        String dockerfile = "FROM --platform=$BUILDPLATFORM golang:1.26.4 AS builder\nARG TARGETOS TARGETARCH\n"
                + "RUN GOOS=$TARGETOS GOARCH=$TARGETARCH go build -o /app .\n";

        assertThat(new FactsParser().parse("go", dockerfile)).isInstanceOf(ParseResult.Parsed.class);
        assertThat(FactExtractorTest.facts(dockerfile).base().name()).isEqualTo("golang");
    }

    @Test
    void theRetrysRewritesLeaveHeredocBodiesAndQuotedArgsAloneAndKeepAContinuedArgsBackslashes() {
        String dockerfile = """
                FROM ubi9
                ARG DESC="two words" OTHER
                ARG A \\
                    B
                RUN <<EOF
                python3 -c 'x = ["a" , "b"]'
                EOF
                CMD ["a" , "b"]
                """;

        assertThat(RetryText.of(dockerfile)).contains(
                "ARG DESC=\"two words\" OTHER\n", "ARG A=$A \\\n    B=$B\n", "python3 -c 'x = [\"a\" , \"b\"]'\n",
                "CMD [\"a\", \"b\"]");
    }

    @Test
    void githubActionsExpressionsInCommentsAndGoTemplateKeywordsDoNotMakeATemplate() {
        assertThat(new FactsParser().parse("ok", """
                # built by CI at ${{ github.sha }}
                FROM ubi9
                RUN docker inspect -f '{{ range .Mounts }}{{ .Source }}{{ end }}' x
                """)).isInstanceOf(ParseResult.Parsed.class);
    }

    @Test
    void aJinjaTemplateThatOnlyParsesOnTheRetryIsStillATemplate() {
        assertThat(new FactsParser().parse("tpl", "FROM ubi9\nENV V={{ spec.version }}\nCMD [\"a\" , \"b\"]\n"))
                .isInstanceOfSatisfying(ParseResult.Failed.class,
                        failed -> assertThat(failed.message()).startsWith(FactsParser.TEMPLATE));
    }

    @Test
    void anArgMixingNamesAndSimpleValuesOrContinuedOverLinesParses() {
        assertThat(new FactsParser().parse("mixed", """
                FROM golang:1.22
                ARG GIT_TAG GIT_COMMIT TARGETARCH CGO_ENABLED=0
                ARG JENKINS_LTS \\
                    CI_UPSTREAM_COMMIT \\
                    CI_UPSTREAM_URL
                RUN make build
                """)).isInstanceOf(ParseResult.Parsed.class);
    }

    @Test
    void anArgWithAnEmptyDefaultParsesAndItsValueIsEmpty() {
        String dockerfile = "ARG HTTP_PROXY=\nARG HTTPS_PROXY=\nFROM --platform=linux/arm64 debian:bookworm\n"
                + "ARG CROSSPLANE_VERSION=\nWORKDIR /workspace\n";

        assertThat(new FactsParser().parse("proxy", dockerfile)).isInstanceOf(ParseResult.Parsed.class);
        assertThat(RetryText.of(dockerfile)).startsWith("ARG HTTP_PROXY=\"\"\nARG HTTPS_PROXY=\"\"\n");
    }
}
