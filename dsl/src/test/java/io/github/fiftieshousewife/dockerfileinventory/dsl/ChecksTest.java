package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChecksTest {

    @Test
    void notBlankReturnsTheValueAndRefusesNullOrBlankNamingWhatWasMissing() {
        assertThat(Checks.notBlank("x", "a name")).isEqualTo("x");
        assertThatIllegalArgumentException().isThrownBy(() -> Checks.notBlank(null, "a name"))
                .withMessage("a name must not be blank");
        assertThatIllegalArgumentException().isThrownBy(() -> Checks.notBlank(" ", "a name"));
    }

    @Test
    void notEmptyReturnsAnUnmodifiableCopyAndRefusesAnEmptyListNamingWhatWasMissing() {
        List<String> paths = new ArrayList<>(List.of("/data"));

        assertThat(Checks.notEmpty(paths, "a VOLUME path")).isUnmodifiable().containsExactly("/data");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Checks.notEmpty(List.of(), "a VOLUME path"))
                .withMessage("a VOLUME path is needed");
    }
}
