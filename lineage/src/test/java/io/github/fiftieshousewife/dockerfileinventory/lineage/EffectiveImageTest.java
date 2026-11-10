package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EffectiveImageTest {

    @Test
    void envAndLabelsKeepTheirKeysSortedSoEveryRunWritesTheSameFile() {
        Map<String, Property<String>> values = new HashMap<>();
        IntStream.range(0, 50).forEach(i -> values.put("KEY_" + (char) ('z' - i % 26) + i, new Property<>("v",
                new SetBy.Unknown())));
        Property<String> none = new Property<>(null, new SetBy.Unknown());

        Property<List<String>> empty = new Property<>(List.of(), new SetBy.Unknown());

        EffectiveImage image = new EffectiveImage(List.of(), none, none, empty, empty, values, values, false);

        assertAll(
                () -> assertThat(List.copyOf(image.env().keySet())).isSorted(),
                () -> assertThat(List.copyOf(image.labels().keySet())).isSorted());
    }
}
