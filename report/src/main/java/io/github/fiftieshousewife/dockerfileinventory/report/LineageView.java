package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

record LineageView(Counts counts, List<ImageRow> images, List<BaseUse> bases, List<String> gaps,
                   List<OutdatedRow> outdated) {

    static final String AMBIGUOUS = "ambiguous: ";

    LineageView {
        images = List.copyOf(images);
        bases = List.copyOf(bases);
        gaps = List.copyOf(gaps);
        outdated = List.copyOf(outdated);
    }

    record OutdatedRow(String image, String base, String newer) {
    }

    record BaseUse(String name, int children) {
    }

    record Counts(int images, double coverage, Map<String, Integer> linksByTier, int gaps, int externalBases,
                  int outdatedBases) {

        Counts {
            linksByTier = Map.copyOf(linksByTier);
        }
    }

    record ImageRow(String name, String parent, String tier, List<String> dockerfiles) {

        ImageRow {
            dockerfiles = List.copyOf(dockerfiles);
        }
    }

    static List<BaseUse> bases(List<ImageRow> images) {
        return images.stream()
                .filter(i -> i.parent() != null && !i.parent().startsWith(AMBIGUOUS))
                .collect(Collectors.groupingBy(ImageRow::parent, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new BaseUse(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(BaseUse::children).reversed().thenComparing(BaseUse::name))
                .toList();
    }
}
