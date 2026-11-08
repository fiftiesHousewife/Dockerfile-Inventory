package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class BaseLags {

    record BaseLag(String base, String tag, String newestTag) {
    }

    // Digit runs compared as numbers of any length: the shorter, leading zeros aside, is the smaller.
    private static final Comparator<String> NUMBER = Comparator.comparingInt(String::length)
            .thenComparing(Comparator.naturalOrder());
    private static final Comparator<String> BY_NUMBERS = (a, b) -> Arrays.compare(numbers(a), numbers(b), NUMBER);

    private final Map<String, List<String>> tagsByImage;

    BaseLags(Map<String, List<String>> tagsByImage) {
        this.tagsByImage = Map.copyOf(tagsByImage);
    }

    Optional<BaseLag> of(String image) {
        String reference = image.replaceFirst("@.*$", "");
        int colon = reference.lastIndexOf(':');
        if (colon < reference.lastIndexOf('/') + 1 || numbers(reference.substring(colon + 1)).length == 0) {
            return Optional.empty();
        }
        String tag = reference.substring(colon + 1);
        String path = CatalogueLinks.path(reference);
        return tagsByImage.getOrDefault(path, List.of()).stream()
                .filter(other -> shape(other).equals(shape(tag)))
                .max(BY_NUMBERS)
                .filter(newest -> BY_NUMBERS.compare(newest, tag) > 0)
                .map(newest -> new BaseLag(path, tag, newest));
    }

    static String shape(String tag) {
        return tag.replaceAll("\\d+", "N");
    }

    static String[] numbers(String tag) {
        return Arrays.stream(tag.split("\\D+")).filter(part -> !part.isEmpty())
                .map(part -> part.replaceFirst("^0+(?=\\d)", "")).toArray(String[]::new);
    }
}
