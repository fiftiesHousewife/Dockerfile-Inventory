package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyCheckTest {

    private static final BaseImage PINNED = ImageBuilderTest.UBI_MINIMAL;

    @Test
    void breachesComeUserFirstThenBaseThenEachInstructionInOrder() {
        Draft build = new Draft("build", new BaseImage("alpine", "3.20", null), null);
        build.add(new Instruction.Raw("RUN apk add git", "copied"));
        Draft last = new Draft(null, null, "build");
        last.add(new Instruction.Raw("ENTRYPOINT ./run.sh", "legacy"));

        assertThat(PolicyCheck.breaches(ImageBuilderTest.POLICY, List.of(build, last)))
                .extracting(PolicyViolation.Breach::rule).containsExactly("S4", "S1", "R1", "H1", "H2");
    }

    @Test
    void userSaysWhetherNoUserWasSetOrWhichOneWasRefused() {
        assertThat(PolicyCheck.user(null)).hasValueSatisfying(breach -> assertThat(breach.message())
                .isEqualTo("the final stage sets no USER"));
        assertThat(PolicyCheck.user("root")).hasValueSatisfying(breach -> assertThat(breach.message())
                .isEqualTo("the final user root is not a numeric, non-root UID"));
        assertThat(PolicyCheck.user("1001:0")).isEmpty();
    }

    @Test
    void numericNonRootReadsTheUidBeforeAnyGroup() {
        assertThat(PolicyCheck.numericNonRoot("1001")).isTrue();
        assertThat(PolicyCheck.numericNonRoot("1001:0")).isTrue();
        assertThat(PolicyCheck.numericNonRoot("0:0")).isFalse();
        assertThat(PolicyCheck.numericNonRoot(":0")).isFalse();
        assertThat(PolicyCheck.numericNonRoot("app")).isFalse();
        assertThat(PolicyCheck.numericNonRoot(null)).isFalse();
    }

    @Test
    void approvalIsABreachOnlyForABaseOffTheList() {
        assertThat(PolicyCheck.approval(ImageBuilderTest.POLICY, PINNED)).isEmpty();
        assertThat(PolicyCheck.approval(ImageBuilderTest.POLICY, new BaseImage("alpine", "3.20", null)))
                .hasValueSatisfying(breach -> assertThat(breach).isEqualTo(new PolicyViolation.Breach("S1",
                        "base alpine:3.20 is not on the approved list")));
    }

    @Test
    void pinNamesTheBuildArgumentThatStandsInForADigest() {
        assertThat(PolicyCheck.pin(PINNED)).isEmpty();
        assertThat(PolicyCheck.pin(new BaseImage("busybox", null, null))).hasValueSatisfying(breach ->
                assertThat(breach.message()).isEqualTo("base busybox is not pinned by digest"));
        assertThat(PolicyCheck.pin(new BaseImage("busybox", null, "sha256:$SHA"))).hasValueSatisfying(breach ->
                assertThat(breach.message()).isEqualTo("base busybox is pinned only by a build argument (SHA)"));
    }

    @Test
    void instructionReportsAShellFormEntrypointAndAnUncleanedInstall() {
        assertThat(PolicyCheck.instruction(new Instruction.Raw("RUN yum install -y git", "copied")))
                .extracting(PolicyViolation.Breach::rule).containsExactly("H1");
        assertThat(PolicyCheck.instruction(new Instruction.User("1001"))).isEmpty();
    }

    @Test
    void shellFormEntrypointIsARawEntrypointNotWrittenAsAJsonArray() {
        assertThat(PolicyCheck.shellFormEntrypoint(new Instruction.Raw("ENTRYPOINT ./run.sh", "legacy")))
                .contains("ENTRYPOINT ./run.sh");
        assertThat(PolicyCheck.shellFormEntrypoint(new Instruction.Raw("ENTRYPOINT  [\"./run.sh\"]", "copied")))
                .isEmpty();
        assertThat(PolicyCheck.shellFormEntrypoint(new Instruction.Raw("CMD ./run.sh", "legacy"))).isEmpty();
        assertThat(PolicyCheck.shellFormEntrypoint(new Instruction.Entrypoint(List.of("./run.sh")))).isEmpty();
    }
}
