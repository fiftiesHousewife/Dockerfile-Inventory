package io.github.fiftieshousewife.dockerfileinventory.tools;

import io.github.fiftieshousewife.dockerfileinventory.lineage.LineageMain;
import io.github.fiftieshousewife.dockerfileinventory.report.ReportMain;
import io.github.fiftieshousewife.dockerfileinventory.rules.RulesMain;
import java.nio.file.Path;
import java.util.Map;

final class DemoStages {

    private final String port;

    DemoStages(String port) {
        this.port = port;
    }

    void run(Path discovery, Path facts, Path out, String token) {
        String lineage = out.resolve("lineage").toString();
        LineageMain.run(lineage(discovery, facts, lineage, token));
        ReportMain.run(report(discovery, facts, lineage, out));
        RulesMain.run(rules(discovery, lineage, out));
        System.out.println("output: " + lineage + ", " + out.resolve("report/index.html") + ", "
                + out.resolve("rules"));
    }

    Map<String, String> lineage(Path discovery, Path facts, String lineage, String token) {
        return Map.of("ARTIFACTORY_URL", "http://localhost:" + port + "/artifactory", "ARTIFACTORY_TOKEN", token,
                "FACTS_DIR", facts.toString(), "DISCOVERY_DIR", discovery.toString(), "OUT_DIR", lineage,
                "ARTIFACTORY_CONCURRENCY", "4", "ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "50");
    }

    static Map<String, String> report(Path discovery, Path facts, String lineage, Path out) {
        return Map.of("FACTS_DIR", facts.toString(), "DISCOVERY_DIR", discovery.toString(), "LINEAGE_DIR",
                lineage, "OUT_DIR", out.resolve("report").toString());
    }

    static Map<String, String> rules(Path discovery, String lineage, Path out) {
        return Map.of("DISCOVERY_DIR", discovery.toString(), "LINEAGE_DIR", lineage, "OUT_DIR",
                out.resolve("rules").toString());
    }
}
