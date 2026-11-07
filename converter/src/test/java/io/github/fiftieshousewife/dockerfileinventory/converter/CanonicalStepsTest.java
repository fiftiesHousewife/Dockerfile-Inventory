package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.facts.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalStepsTest {

    @Test
    void ofPrefixesEachCanonicalStepWithItsKeyword() {
        assertThat(CanonicalSteps.of(List.of(new Step("USER", "\"1001\""), new Step("CMD", "[ \"a\" ]"))))
                .containsExactly("USER 1001", "CMD [\"a\"]");
    }

    @Test
    void anInstallIgnoresFlagAndPackageOrder() {
        assertThat(CanonicalSteps.canonical(new Step("RUN", "microdnf install tar git -y && microdnf clean all")))
                .isEqualTo("MICRODNF flags=-y packages=git,tar");
    }

    @Test
    void envAndLabelWordsAreSortedSoOrderDoesNotCount() {
        assertThat(CanonicalSteps.canonical(new Step("ENV", "B=2 A=1 B=2"))).isEqualTo("A=1 B=2");
    }

    @Test
    void bracesAreDroppedUnlessANameCharacterFollows() {
        assertThat(CanonicalSteps.canonical(new Step("RUN", "echo ${A} ${A}B"))).isEqualTo("echo $A ${A}B");
    }

    @Test
    void jsonRewritesAnArrayWithoutSpacingAndLeavesOtherTextAlone() {
        assertThat(List.of("[ \"a\",  \"b\" ]", "[not json", "plain")).map(CanonicalSteps::json)
                .containsExactly("[\"a\",\"b\"]", "[not json", "plain");
    }
}
