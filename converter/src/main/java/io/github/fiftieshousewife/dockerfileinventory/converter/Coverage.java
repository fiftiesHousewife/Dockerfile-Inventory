package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.ObjectMapper;

final class Coverage {

    private final Map<String, Double> images = new TreeMap<>();
    private final Map<String, Integer> rawReasons = new TreeMap<>();
    private Map<String, Integer> javaRoundTrip;

    void add(String blobSha, Conversion conversion) {
        images.put(blobSha, of(conversion));
        conversion.raw().forEach(raw -> rawReasons.merge(raw.reason(), 1, Integer::sum));
    }

    String json(ObjectMapper json) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("images", images);
        summary.put("rawReasons", rawReasons);
        if (javaRoundTrip != null) {
            summary.put("javaRoundTrip", javaRoundTrip);
        }
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
    }

    void javaRoundTrip(int passed, int of) {
        javaRoundTrip = new LinkedHashMap<>(Map.of("passed", passed));
        javaRoundTrip.put("of", of);
    }

    static double of(Conversion conversion) {
        List<Instruction> all = conversion.model().stages().stream()
                .flatMap(stage -> stage.instructions().stream())
                .toList();
        long typed = all.stream().filter(i -> !(i instanceof Raw)).count();
        return all.isEmpty() ? 1.0 : (double) typed / all.size();
    }
}
