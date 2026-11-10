package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleRatesTest {

    private static final Map<String, List<Location>> LOCATIONS = Map.of(
            "a", List.of(new Location("o/one", "Dockerfile"), new Location("o/two", "Dockerfile")),
            "b", List.of(new Location("o/three", "Dockerfile")));

    @Test
    void countsAreFindingsDistinctFilesAndTheirLocations() {
        List<Finding> findings = List.of(finding("S3", "a"), finding("S3", "a"), finding("S3", "b"));

        assertThat(RuleRates.counts(findings, LOCATIONS))
                .containsExactly(Map.entry("findings", 3), Map.entry("files", 2), Map.entry("locations", 5));
    }

    @Test
    void ratesListTheSkippedRulesSortedAndEachRulesCounts() {
        Map<String, Object> rates = RuleRates.of(List.of(finding("S3", "a"), finding("H1", "c")), LOCATIONS,
                List.of("S4", "R2"));

        assertAll(
                () -> assertThat(rates.keySet()).containsExactly("skipped", "rules"),
                () -> assertThat(rates.get("skipped")).asString().isEqualTo("[R2, S4]"),
                () -> assertThat(rates.get("rules")).asString().isEqualTo("{H1={findings=1, files=1, locations=0}, "
                        + "S3={findings=1, files=1, locations=2}}"));
    }

    private static Finding finding(String rule, String blob) {
        return new Finding(rule, Severity.LOW, blob, null, "m", null, List.of());
    }
}
