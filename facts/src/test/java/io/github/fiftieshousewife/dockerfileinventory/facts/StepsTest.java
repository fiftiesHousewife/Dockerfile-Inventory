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

    static List<Step> steps(String dockerfile) {
        ParseResult parsed = new FactsParser().parse("sha", dockerfile);
        assertThat(parsed).isInstanceOf(ParseResult.Parsed.class);
        return Steps.of(((ParseResult.Parsed) parsed).file());
    }
}
