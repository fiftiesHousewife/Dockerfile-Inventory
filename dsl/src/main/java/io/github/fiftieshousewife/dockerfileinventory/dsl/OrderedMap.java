package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

/** Pairs in the order given, for {@code ENV}, {@code LABEL} and {@code HEALTHCHECK}, which render in map order. */
public final class OrderedMap {

    private OrderedMap() {
    }

    /** {@code of("A", "1", "B", "2")}: keys and values alternating. */
    public static Map<String, String> of(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("a key without a value: " + keysAndValues[keysAndValues.length - 1]);
        }
        Map<String, String> map = new LinkedHashMap<>();
        IntStream.range(0, keysAndValues.length / 2)
                .forEach(i -> map.put(keysAndValues[2 * i], keysAndValues[2 * i + 1]));
        return Collections.unmodifiableMap(map);
    }
}
