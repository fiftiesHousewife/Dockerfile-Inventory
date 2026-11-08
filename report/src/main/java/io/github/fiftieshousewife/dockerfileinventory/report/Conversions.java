package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class Conversions {

    record Conversion(String roundTrip, String difference) {
    }

    private static final Conversion NOT_RUN = new Conversion("not run", null);

    private Conversions() {
    }

    static Map<String, Conversion> read(Path converterDir) {
        Map<String, Conversion> conversions = new HashMap<>();
        if (converterDir != null) {
            JsonLines.read(converterDir.resolve("roundtrip.jsonl")).forEach(row -> conversions.put(
                    row.path("blobSha").asString(), new Conversion(row.path("passed").asBoolean() ? "passed" : "failed",
                            row.path("difference").stringValue(null))));
        }
        return conversions;
    }

    static Conversion of(Map<String, Conversion> conversions, String blobSha) {
        return conversions.getOrDefault(blobSha, NOT_RUN);
    }
}
