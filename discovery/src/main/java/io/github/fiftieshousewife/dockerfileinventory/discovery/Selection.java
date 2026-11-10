package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// names: repositories named exactly, owner/name; strings: a repository whose name (not owner) holds one is kept too.
record Selection(List<String> names, List<String> strings) {

    static final Selection ALL = new Selection(List.of(), List.of());

    Selection {
        names = List.copyOf(names);
        strings = List.copyOf(strings);
    }

    static Selection from(Map<String, String> env) {
        return new Selection(listed(env.get("REPOSITORIES")), listed(env.get("REPOSITORY_NAME_CONTAINS")));
    }

    static List<String> listed(String value) {
        return value == null ? List.of() : Arrays.stream(value.split(",")).map(String::strip)
                .filter(part -> !part.isEmpty()).toList();
    }

    List<Repository> of(List<Repository> listed) {
        if (equals(ALL)) {
            return listed;
        }
        Set<String> present = listed.stream().map(r -> lower(r.nameWithOwner())).collect(Collectors.toSet());
        names.stream().filter(name -> !present.contains(lower(name))).findFirst().ifPresent(missing -> {
            throw new SelectionNotFound("no repository " + missing + " in the organisations' listing (or the "
                    + "credential cannot read it)");
        });
        List<Repository> kept = listed.stream().filter(this::keeps).toList();
        if (kept.isEmpty()) {
            throw new SelectionNotFound("no repository's name holds " + String.join(" or ", strings));
        }
        return kept;
    }

    boolean keeps(Repository repository) {
        String full = lower(repository.nameWithOwner());
        String name = full.substring(full.indexOf('/') + 1);
        return names.stream().anyMatch(n -> lower(n).equals(full))
                || strings.stream().anyMatch(s -> name.contains(lower(s)));
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
