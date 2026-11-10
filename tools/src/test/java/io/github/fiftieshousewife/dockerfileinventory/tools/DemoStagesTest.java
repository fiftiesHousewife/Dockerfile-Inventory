package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemoStagesTest {

    private static final Path DISCOVERY = Path.of("out", "discovery");
    private static final Path FACTS = Path.of("out", "facts");
    private static final Path OUT = Path.of("out", "demo");

    @Test
    void lineageReadsTheLocalJcrOnItsPortWithTheTokenAndTheEarlierStagesOutput() {
        assertThat(new DemoStages("18082").lineage(DISCOVERY, FACTS, "lineage", "t1")).isEqualTo(Map.of(
                "ARTIFACTORY_URL", "http://localhost:18082/artifactory", "ARTIFACTORY_TOKEN", "t1",
                "FACTS_DIR", FACTS.toString(), "DISCOVERY_DIR", DISCOVERY.toString(), "OUT_DIR", "lineage",
                "ARTIFACTORY_CONCURRENCY", "4", "ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "50"));
    }

    @Test
    void theReportReadsFactsDiscoveryAndLineageIntoItsOwnFolder() {
        assertThat(DemoStages.report(DISCOVERY, FACTS, "lineage", OUT)).isEqualTo(Map.of(
                "FACTS_DIR", FACTS.toString(), "DISCOVERY_DIR", DISCOVERY.toString(), "LINEAGE_DIR", "lineage",
                "OUT_DIR", OUT.resolve("report").toString()));
    }

    @Test
    void theRulesReadDiscoveryAndLineageIntoTheirOwnFolder() {
        assertThat(DemoStages.rules(DISCOVERY, "lineage", OUT)).isEqualTo(Map.of(
                "DISCOVERY_DIR", DISCOVERY.toString(), "LINEAGE_DIR", "lineage",
                "OUT_DIR", OUT.resolve("rules").toString()));
    }
}
