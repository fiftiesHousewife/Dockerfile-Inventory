package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class BaseCatalogue {

    static final String UNRESOLVED = "unresolved";

    private BaseCatalogue() {
    }

    static List<BaseRow> of(Estate estate, EolList eol) {
        Map<List<String>, List<Dockerfile>> byBase = new LinkedHashMap<>();
        estate.dockerfiles().forEach(d -> byBase.computeIfAbsent(key(d.base()), k -> new ArrayList<>()).add(d));
        return byBase.entrySet().stream()
                .map(e -> row(e.getKey().get(0), e.getKey().get(1), e.getValue(), eol))
                .sorted(Comparator.comparingInt(BaseRow::locations).reversed().thenComparing(BaseRow::path))
                .toList();
    }

    static BaseRow row(String path, String tag, List<Dockerfile> users, EolList eol) {
        int locations = users.stream().mapToInt(d -> d.locations().size()).sum();
        int pinned = users.stream()
                .filter(d -> d.base().digest() != null)
                .mapToInt(d -> d.locations().size())
                .sum();
        Optional<EolList.Entry> status = eol.lookup(path, tag);
        return new BaseRow(path, tag, Passes.family(users.getFirst().base()), locations, pinned,
                status.map(EolList.Entry::status).orElse("unknown"),
                status.map(EolList.Entry::eolDate).orElse(null));
    }

    static List<String> key(Base base) {
        String name = base.name() == null ? "" : base.name();
        return name.contains("$") ? Arrays.asList(UNRESOLVED, null) : Arrays.asList(path(base), base.tag());
    }

    // Docker's rule for a registry host, as facts' fromPath applies it.
    static String path(Base base) {
        String name = base.name() == null ? "" : base.name();
        int slash = name.indexOf('/');
        String first = slash < 0 ? "" : name.substring(0, slash);
        boolean host = first.contains(".") || first.contains(":") || first.equals("localhost");
        return host ? name.substring(slash + 1) : name;
    }
}
