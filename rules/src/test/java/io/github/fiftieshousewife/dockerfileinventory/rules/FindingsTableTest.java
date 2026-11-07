package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FindingsTableTest {

    private static final Finding R1 = new Finding("R1", Severity.MEDIUM, "a", null, "base x, unpinned", null,
            List.of());
    private static final Finding S2 = new Finding("S2", Severity.CRITICAL, "b", 3, "ENV P", "hash", List.of("c", "d"));

    @Test
    void theTableHasOneRowPerFindingPerLocationAndNoneForAFileWithNoLocation() {
        Map<String, List<Location>> locations = Map.of("a", List.of(new Location("o/one", "Dockerfile"),
                new Location("o/two", "Dockerfile")));

        assertThat(FindingsTable.csv(List.of(R1, S2), locations)).isEqualTo(FindingsTable.HEADER
                + "R1,MEDIUM,o/one,Dockerfile,a,,\"base x, unpinned\",,\r\n"
                + "R1,MEDIUM,o/two,Dockerfile,a,,\"base x, unpinned\",,\r\n");
    }

    @Test
    void aRowCarriesTheLineTheValueHashAndTheContributingDockerfiles() {
        assertThat(FindingsTable.row(S2, new Location("o/r", "p")))
                .isEqualTo("S2,CRITICAL,o/r,p,b,3,ENV P,hash,c;d\r\n");
    }

    @Test
    void theDdlDeclaresEveryColumnOfTheTable() {
        assertThat(FindingsTable.DDL).contains("CREATE TABLE findings").contains("value_hash", "contributing");
    }
}
