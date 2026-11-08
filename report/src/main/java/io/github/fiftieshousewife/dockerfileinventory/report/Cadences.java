package io.github.fiftieshousewife.dockerfileinventory.report;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class Cadences {

    record Cadence(int pushesInYear, Integer medianDaysBetween, Integer daysAfterParent) {
    }

    private final Map<String, RegistryImage> images;
    private final Map<String, List<Instant>> pushesByName;
    private final Instant yearStart;

    Cadences(Map<String, RegistryImage> images, Map<String, List<Instant>> pushesByName, Instant newestPush) {
        this.images = Map.copyOf(images);
        this.pushesByName = Map.copyOf(pushesByName);
        this.yearStart = newestPush.atZone(ZoneOffset.UTC).minusYears(1).toInstant();
    }

    Cadence of(String configDigest) {
        RegistryImage image = images.get(configDigest);
        List<Instant> pushes = image.names().stream().map(Cadences::imageName).distinct()
                .flatMap(name -> pushesByName.getOrDefault(name, List.of()).stream()).distinct().sorted().toList();
        return new Cadence((int) pushes.stream().filter(push -> push.isAfter(yearStart)).count(),
                medianDaysBetween(pushes), daysAfterParent(image));
    }

    static Integer medianDaysBetween(List<Instant> sorted) {
        List<Long> gaps = IntStream.range(1, sorted.size())
                .mapToObj(i -> Duration.between(sorted.get(i - 1), sorted.get(i)).toDays()).sorted().toList();
        if (gaps.isEmpty()) {
            return null;
        }
        int middle = gaps.size() / 2;
        return (int) (gaps.size() % 2 == 1 ? gaps.get(middle) : (gaps.get(middle - 1) + gaps.get(middle)) / 2);
    }

    Integer daysAfterParent(RegistryImage image) {
        RegistryImage parent = image.parent() == null ? null : images.get(image.parent());
        if (parent == null || image.created() == null || parent.created() == null) {
            return null;
        }
        return (int) Duration.between(Instant.parse(parent.created()), Instant.parse(image.created())).toDays();
    }

    static String imageName(String nameAndTag) {
        return nameAndTag.substring(0, nameAndTag.lastIndexOf(':'));
    }
}
