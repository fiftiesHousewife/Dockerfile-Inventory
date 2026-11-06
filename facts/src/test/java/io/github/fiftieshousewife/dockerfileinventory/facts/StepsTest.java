package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StepsTest {

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

    static List<Step> steps(String dockerfile) {
        ParseResult parsed = new FactsParser().parse("sha", dockerfile);
        assertThat(parsed).isInstanceOf(ParseResult.Parsed.class);
        return Steps.of(((ParseResult.Parsed) parsed).file());
    }
}
