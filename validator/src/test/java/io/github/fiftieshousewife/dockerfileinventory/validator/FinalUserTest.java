package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class FinalUserTest {

    private final FinalUser check = new FinalUser();

    @Test
    void aRootUnsetOrNamedUserIsAProblemAndANumericOneIsNot() {
        assertAll(
                () -> assertThat(check.problems(subject("007a290c"), V2)).containsExactly(
                        "the image runs as root; OpenShift runs it as an arbitrary UID in group 0, without root's "
                                + "rights"),
                () -> assertThat(check.problems(subject("00b7e9b0"), V2)).containsExactly(
                        "the image sets no user, so it runs as its base's, root unless the base sets one; OpenShift "
                                + "runs it as an arbitrary UID in group 0, without root's rights"),
                () -> assertThat(check.problems(subject("002742f1"), V2)).containsExactly(
                        "the image runs as the named user nonroot; OpenShift runs an arbitrary UID with no such name"),
                () -> assertThat(check.problems(subject("08bf2be5"), V2)).isEmpty());
    }

    @Test
    void anUnsetUserIsInferredAndAWrittenOneRead() {
        assertAll(
                () -> assertThat(check.certainty(subject("00b7e9b0"))).isEqualTo(Certainty.INFERRED),
                () -> assertThat(check.certainty(subject("007a290c"))).isEqualTo(Certainty.READ));
    }
}
