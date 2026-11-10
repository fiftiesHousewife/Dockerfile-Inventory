package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class RunFlagTest {

    @Test
    void aMountKeepsItsOptionsAsWrittenAndIsABindUnlessItNamesAType() {
        RunFlag.Mount cache = RunFlag.Mount.parse("type=cache,target=/root/.m2,sharing=locked");
        RunFlag.Mount bind = RunFlag.Mount.parse("source=.,target=/src,ro");

        assertThat(List.of(cache.type(), bind.type(), cache.spec(), bind.spec())).containsExactly("cache", "bind",
                "type=cache,target=/root/.m2,sharing=locked", "source=.,target=/src,ro");
    }

    @Test
    void eachFlagIsWrittenAsDockerWritesIt() {
        assertThat(List.of(RunFlag.Mount.parse("type=secret,id=token,env=TOKEN"), new RunFlag.Network("none"),
                new RunFlag.Security("insecure")))
                .map(RunFlag::written)
                .containsExactly("--mount=type=secret,id=token,env=TOKEN", "--network=none", "--security=insecure");
    }

    @Test
    void anEmptyMountOrABlankModeIsRefused() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new RunFlag.Mount(List.of())),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new RunFlag.Network(" ")),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new RunFlag.Security("")));
    }
}
