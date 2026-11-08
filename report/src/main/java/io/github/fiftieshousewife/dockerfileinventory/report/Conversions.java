package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Secrets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

final class Conversions {

    record Conversion(String roundTrip, String difference, String javaRoundTrip, String javaDifference) {
    }

    private static final String NOT_RUN_YET = "not run";
    private static final Conversion NOT_RUN = new Conversion(NOT_RUN_YET, null, NOT_RUN_YET, null);

    private Conversions() {
    }

    static Map<String, Conversion> read(Path converterDir) {
        Map<String, Conversion> conversions = new HashMap<>();
        if (converterDir != null) {
            JsonLines.read(converterDir.resolve("roundtrip.jsonl")).forEach(row -> conversions.put(
                    row.path("blobSha").asString(), conversion(row)));
        }
        return conversions;
    }

    static Conversion conversion(JsonNode row) {
        JsonNode java = row.path("javaPassed");
        return new Conversion(outcome(row.path("passed")), shown(row.path("difference").stringValue(null)),
                java.isBoolean() ? outcome(java) : NOT_RUN_YET, shown(row.path("javaDifference").stringValue(null)));
    }

    static String outcome(JsonNode passed) {
        return passed.asBoolean() ? "passed" : "failed";
    }

    static String shown(String difference) {
        return difference != null && Arrays.stream(difference.split("[^A-Za-z0-9_.-]+")).anyMatch(Secrets::secretName)
                ? "(hidden: it names a secret)" : difference;
    }

    static Conversion of(Map<String, Conversion> conversions, String blobSha) {
        return conversions.getOrDefault(blobSha, NOT_RUN);
    }
}
