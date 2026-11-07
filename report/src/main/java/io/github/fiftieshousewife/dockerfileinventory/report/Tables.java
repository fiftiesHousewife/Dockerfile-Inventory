package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Table.Type.DATE;
import static io.github.fiftieshousewife.dockerfileinventory.report.Table.Type.NUMBER;
import static io.github.fiftieshousewife.dockerfileinventory.report.Table.Type.TEXT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class Tables {

    private Tables() {
    }

    static List<Table> of(Estate estate, List<Group> groups, Ranking ranking, List<BaseRow> bases) {
        Map<String, String> archetypeOf = new HashMap<>();
        groups.forEach(g -> g.blobShas().forEach(sha -> archetypeOf.put(sha, g.key())));
        return List.of(dockerfiles(estate, archetypeOf), locations(estate), archetypes(ranking), bases(bases));
    }

    static Table dockerfiles(Estate estate, Map<String, String> archetypeOf) {
        List<List<Object>> rows = new ArrayList<>();
        estate.dockerfiles().forEach(d -> rows.add(Arrays.asList(d.blobSha(), d.signature(),
                BaseCatalogue.path(d.base()), d.base().tag(), d.base().digest(), d.userKind(),
                name(estate.effectiveUsers().get(d.blobSha())), d.runs(), d.opaqueRuns(),
                archetypeOf.get(d.blobSha()), null)));
        estate.failures().forEach(f -> rows.add(Arrays.asList(f.blobSha(), null, null, null, null, null, null, null,
                null, archetypeOf.get(f.blobSha()), f.message())));
        return new Table("dockerfiles", List.of(text("blob_sha"), text("signature"), text("base_path"),
                text("base_tag"), text("base_digest"), text("user_kind"), text("effective_user_kind"),
                number("runs"), number("opaque_runs"), text("archetype_key"), text("parse_error")), rows);
    }

    static Table locations(Estate estate) {
        Stream<List<Object>> parsed = estate.dockerfiles().stream()
                .flatMap(d -> d.locations().stream().map(l -> row(l, d.blobSha())));
        Stream<List<Object>> failed = estate.failures().stream()
                .flatMap(f -> f.locations().stream().map(l -> row(l, f.blobSha())));
        return new Table("locations", List.of(text("repository"), text("path"), text("scope"), text("blob_sha")),
                Stream.concat(parsed, failed).toList());
    }

    private static List<Object> row(Location location, String blobSha) {
        return List.of(location.repository(), location.path(), location.scope(), blobSha);
    }

    static Table archetypes(Ranking ranking) {
        return new Table("archetypes", List.of(number("rank"), text("archetype_key"), number("locations"),
                number("blobs"), text("signature"), text("representative_blob")),
                ranking.archetypes().stream().<List<Object>>map(a -> Arrays.asList(a.rank(), a.key(), a.locations(),
                        a.blobShas().size(), a.signature(), a.representative())).toList());
    }

    static Table bases(List<BaseRow> bases) {
        return new Table("bases", List.of(text("path"), text("tag"), text("family"), number("locations"),
                number("pinned_locations"), text("status"), new Table.Column("eol_date", DATE)),
                bases.stream().<List<Object>>map(b -> Arrays.asList(b.path(), b.tag(), b.family(), b.locations(),
                        b.pinnedLocations(), b.status(), b.eolDate())).toList());
    }

    static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Table.Column text(String name) {
        return new Table.Column(name, TEXT);
    }

    private static Table.Column number(String name) {
        return new Table.Column(name, NUMBER);
    }
}
