package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class StepsTest {

    /** Shared with lineage, which must reduce the image's real history to the same steps. */
    private static final Path CONTRACT = Path.of("../testdata/contracts/python-311.steps.json");

    @Test
    void shellRunJoinsContinuationsDropsCommentLinesInsideAndCollapsesWhitespace() {
        assertThat(steps("""
                FROM ubi9/ubi-minimal
                RUN \\
                    microdnf install -y git && \\
                    # keep the image small \\
                    microdnf clean all
                """)).containsExactly(new Step("RUN", "microdnf install -y git && microdnf clean all"));
    }

    @Test
    void execFormAndHeredocRunAreKeptAsWrittenAndCollapsed() {
        assertThat(steps("""
                FROM ubi9/ubi-minimal
                RUN ["microdnf",   "install", "-y", "git"]
                RUN <<EOT
                set -e
                microdnf clean all
                EOT
                """)).containsExactly(
                        new Step("RUN", "[\"microdnf\", \"install\", \"-y\", \"git\"]"),
                        new Step("RUN", "<<EOT set -e microdnf clean all EOT"));
    }

    @Test
    void copyAndAddGiveTheirDestinationWithFlagsIgnored() {
        assertThat(steps("""
                FROM ubi9/ubi-minimal
                COPY --chown=1001:0 3.11/s2i/bin/ $STI_SCRIPTS_PATH
                COPY 3.11/root/   /
                ADD ["src dir/", "/opt/app dir/"]
                COPY --chown=1001:0 ["a b", "/c d/"]
                """)).containsExactly(
                        new Step("COPY", "$STI_SCRIPTS_PATH"),
                        new Step("COPY", "/"),
                        new Step("ADD", "/opt/app dir/"),
                        new Step("COPY", "/c d/"));
    }

    @Test
    void labelAndEnvGiveSortedKeyNamesAndOtherInstructionsTheirCollapsedValue() {
        assertThat(steps("""
                FROM ubi9/ubi-minimal
                LABEL summary="$SUMMARY" \
                      "io.k8s.display-name"="Python 3.11" \
                      description="say \\"hi\\" a=b"
                ENV SUMMARY="Platform for \
                building" PATH=$HOME/bin:$PATH
                ENV container oci
                USER 1001
                WORKDIR   ${HOME}
                EXPOSE 8080
                ENTRYPOINT ["container-entrypoint"]
                CMD $STI_SCRIPTS_PATH/usage
                """)).containsExactly(
                        new Step("LABEL", "description io.k8s.display-name summary"),
                        new Step("ENV", "PATH SUMMARY"),
                        new Step("ENV", "container"),
                        new Step("USER", "1001"),
                        new Step("WORKDIR", "${HOME}"),
                        new Step("EXPOSE", "8080"),
                        new Step("ENTRYPOINT", "[\"container-entrypoint\"]"),
                        new Step("CMD", "$STI_SCRIPTS_PATH/usage"));
    }

    @Test
    void aFinalStageFromAnEarlierStageStartsWithItsStepsAndArgLeavesNone() {
        assertThat(steps("""
                ARG BASE=ubi9/ubi-minimal
                FROM $BASE AS base
                ARG APP=1
                RUN microdnf install -y git

                # the build stage does not reach the image
                FROM golang:1.23 AS build
                RUN go build ./...

                FROM base
                USER 1001
                """)).containsExactly(new Step("RUN", "microdnf install -y git"), new Step("USER", "1001"));
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aStageNamedLikeItsOwnBaseIsNotItsOwnParent() {
        assertThat(steps("""
                FROM app AS app
                RUN make
                """)).containsExactly(new Step("RUN", "make"));
    }

    @Test
    void theRealPython311DockerfileGivesTheStepsItsRealBuildHistoryGives() throws IOException {
        JsonNode contract = new ObjectMapper().readTree(CONTRACT.toFile());
        List<Step> expected = new ArrayList<>();
        contract.path("steps").forEach(s -> expected.add(
                new Step(s.path("keyword").asString(), s.path("text").asString())));
        String dockerfile;
        try (InputStream in = StepsTest.class.getResourceAsStream("/fixtures/dockerfiles/python-311.Dockerfile")) {
            dockerfile = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(steps(dockerfile)).containsExactlyElementsOf(expected);
        assertThat(NormalForms.fromPath(FactExtractorTest.facts(dockerfile).base()))
                .isEqualTo(contract.path("fromPath").asString());
    }

    @Test
    void aLegacyLabelGivesOneKeyAsALegacyEnvDoes() {
        assertThat(steps("""
                FROM ubi9
                LABEL MAINTAINER "Marek Polacek <polacek@redhat.com>"
                """)).containsExactly(new Step("LABEL", "MAINTAINER"));
    }

    @Test
    void aBackslashFollowedByTrailingSpacesContinuesTheLine() {
        assertThat(steps("FROM ubi9\nRUN make \\  \n    && make install\n"))
                .containsExactly(new Step("RUN", "make && make install"));
    }

    static List<Step> steps(String dockerfile) {
        ParseResult parsed = new FactsParser().parse("sha", dockerfile);
        assertThat(parsed).isInstanceOf(ParseResult.Parsed.class);
        return Steps.of(((ParseResult.Parsed) parsed).file());
    }
}
