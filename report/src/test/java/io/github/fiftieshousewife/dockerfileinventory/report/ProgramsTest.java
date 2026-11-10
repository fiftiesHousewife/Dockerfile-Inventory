package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProgramsTest {

    private static CatalogueEntry.Command exec(String... arguments) {
        return new CatalogueEntry.Command("exec", List.of(arguments));
    }

    @Test
    void aProgramIsTheEntrypointThenTheCommand() {
        assertAll(
                () -> assertThat(Programs.of(exec("/manager"), null)).isEqualTo("/manager"),
                () -> assertThat(Programs.of(exec("/opm"), exec("serve", "/configs"))).isEqualTo("/opm serve /configs"),
                () -> assertThat(Programs.of(null, exec("python", "app.py"))).isEqualTo("python app.py"),
                () -> assertThat(Programs.of(null, null)).isEqualTo("its base's"));
    }

    @Test
    void anArchetypeCountsTheDifferentProgramsItsDockerfilesStart() {
        assertThat(Programs.distinct(List.of("a", "b", "c", "unknown"), Map.of("a", "/manager", "b", "/manager",
                "c", "/operator"))).isEqualTo(2);
    }

    @Test
    void theSameRecipeSplitsIntoTheSameProgramAndAnother() {
        assertThat(Programs.split("/manager", List.of("b", "c", "d"), Map.of("b", "/manager", "c", "/operator")))
                .isEqualTo(new Programs.Split(List.of("b"), List.of("c", "d")));
    }
}
