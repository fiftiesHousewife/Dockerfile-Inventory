package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The bases the estate actually builds from, by locations: the start of Phase 3's allow-list. */
public final class BaseCatalogue {

    private BaseCatalogue() {
    }

    public static List<BaseRow> of(Estate estate, EolList eol) {
        Map<List<String>, List<Dockerfile>> byBase = new LinkedHashMap<>();
        estate.dockerfiles().forEach(d -> byBase
                .computeIfAbsent(java.util.Arrays.asList(path(d.base()), d.base().tag()), k -> new ArrayList<>())
                .add(d));
        return byBase.entrySet().stream().map(e -> {
            String path = e.getKey().get(0);
            String tag = e.getKey().get(1);
            List<Dockerfile> users = e.getValue();
            int locations = users.stream().mapToInt(d -> d.locations().size()).sum();
            int pinned = users.stream().filter(d -> d.base().digest() != null).mapToInt(d -> d.locations().size()).sum();
            return new BaseRow(path, tag, Passes.family(users.getFirst().base()), locations, pinned, "unknown", null);
        }).sorted(Comparator.comparingInt(BaseRow::locations).reversed().thenComparing(BaseRow::path)).toList();
    }

    /** The image path without registry host, by Docker's rule, as facts' {@code fromPath}. */
    static String path(Base base) {
        String name = base.name() == null ? "" : base.name();
        int slash = name.indexOf('/');
        String first = slash < 0 ? "" : name.substring(0, slash);
        boolean host = first.contains(".") || first.contains(":") || first.equals("localhost");
        return host ? name.substring(slash + 1) : name;
    }
}
