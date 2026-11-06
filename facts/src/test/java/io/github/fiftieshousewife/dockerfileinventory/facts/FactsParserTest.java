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

        assertThat(FactsParser.withoutComments(text)).isEqualTo("""
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

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/fixtures/dockerfiles", name));
    }
}
