package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.model.Policy;
import io.github.fiftieshousewife.dockerfile.model.PolicyViolation;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedClassesTest {

    private static final Conversion UNAPPROVED_BASE = Convert.of(Parsed.of("FROM ubi9:9.4\nUSER 1001\n"));

    @TempDir
    Path out;

    @Test
    void optedInIsOneRepositoryPerLineWithoutBlankLinesOrComments() {
        assertThat(GeneratedClasses.optedIn("payments/api\n\n  # comment\n  platform/tools  \n"))
                .containsExactlyInAnyOrder("payments/api", "platform/tools");
    }

    @Test
    void withoutAnApprovedListTheUnapprovedBaseIsNotABreach() {
        assertThat(generated(false).breaches(UNAPPROVED_BASE)).extracting(PolicyViolation.Breach::rule)
                .doesNotContain("S1");
    }

    @Test
    void withAnApprovedListTheUnapprovedBaseIsABreach() {
        assertThat(generated(true).breaches(UNAPPROVED_BASE)).extracting(PolicyViolation.Breach::rule).contains("S1");
    }

    @Test
    void aClassIsWrittenOnlyForEachOptedInLocationOfTheBlob() {
        generated(false).generate("blob", UNAPPROVED_BASE);

        assertThat(out).satisfies(
                o -> assertThat(o.resolve("generated/images/payments/PaymentsApiImage.java")).exists(),
                o -> assertThat(o.resolve("generated/images/platform")).doesNotExist());
    }

    private GeneratedClasses generated(boolean approvedBasesListed) {
        Map<String, List<Location>> locations = Map.of("blob",
                List.of(new Location("payments/api", "Dockerfile"), new Location("platform/tools", "Dockerfile")));
        return new GeneratedClasses(Set.of("payments/api"), locations, Policy.of(List.of()), approvedBasesListed,
                new RunFiles(out, out));
    }
}
