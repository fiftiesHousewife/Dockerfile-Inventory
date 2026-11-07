package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SamplesTest {

    @Test
    void roundRobinTakesOneFromEachGroupInTurnUpToTheLimit() {
        List<List<String>> groups = List.of(List.of("a1", "a2", "a3"), List.of("b1"), List.of("c1", "c2"));

        assertThat(Samples.roundRobin(groups, 10)).containsExactly("a1", "b1", "c1", "a2", "c2", "a3");
        assertThat(Samples.roundRobin(groups, 4)).containsExactly("a1", "b1", "c1", "a2");
        assertThat(Samples.roundRobin(List.of(), 4)).isEmpty();
    }

    @Test
    void byOrganisationGivesOneSampleRowPerLocationUnderItsOwner() {
        Map<String, List<Location>> locations = Map.of("a", List.of(new Location("pay/api", "Dockerfile"),
                new Location("ops/base", "Dockerfile"), new Location("pay/web", "Dockerfile")));

        assertThat(Samples.byOrganisation(List.of(finding("a", 2)), locations)).satisfies(rows -> {
            assertThat(rows.keySet()).containsExactly("ops", "pay");
            assertThat(rows.get("pay")).containsExactly("S3,HIGH,pay/api,Dockerfile,a,2,m,\r\n",
                    "S3,HIGH,pay/web,Dockerfile,a,2,m,\r\n");
        });
    }

    @Test
    void shuffledIsTheSameOrderOnEveryRunWhateverTheInputOrder() {
        List<String> rows = IntStream.range(0, 20).mapToObj(i -> "row" + i).toList();

        assertThat(Samples.shuffled(organisation(rows))).isEqualTo(Samples.shuffled(organisation(rows.reversed())));
    }

    @Test
    void aSampleHasTheHeaderAndAtMostFiftyRows() {
        List<Location> many = IntStream.range(0, 80).mapToObj(i -> new Location("org" + i + "/r", "Dockerfile"))
                .toList();

        assertThat(Samples.sample(List.of(finding("a", null)), Map.of("a", many)).split("\r\n"))
                .hasSize(1 + Samples.SIZE)
                .startsWith(Samples.HEADER.strip());
    }

    @Test
    void ofWritesOneSamplePerRuleInRuleOrder() {
        Map<String, List<Location>> locations = Map.of("a", List.of(new Location("o/r", "Dockerfile")));
        Finding h1 = new Finding("H1", Severity.LOW, "a", null, "m", null, List.of());

        assertThat(Samples.of(List.of(finding("a", null), h1), locations)).containsOnlyKeys("H1", "S3");
    }

    private static Map<String, List<String>> organisation(List<String> rows) {
        return new TreeMap<>(Map.of("org", new ArrayList<>(rows)));
    }

    private static Finding finding(String blob, Integer line) {
        return new Finding("S3", Severity.HIGH, blob, line, "m", null, List.of());
    }
}
