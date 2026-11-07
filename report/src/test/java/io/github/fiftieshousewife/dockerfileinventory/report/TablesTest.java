package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TablesTest {

    @Test
    void writesDockerfilesLocationsArchetypesAndBasesWithEachDockerfilesArchetype() {
        List<Group> groups = Passes.signatureGroups(PassesTest.ESTATE);
        Ranking ranking = Archetypes.rank(groups, PassesTest.ESTATE);
        List<BaseRow> bases = BaseCatalogue.of(PassesTest.ESTATE, EolList.empty());

        Map<String, Table> tables = Tables.of(PassesTest.ESTATE, groups, ranking, bases).stream()
                .collect(Collectors.toMap(Table::name, Function.identity()));

        assertThat(tables).satisfies(t -> {
            assertThat(t).containsOnlyKeys("dockerfiles", "locations", "archetypes", "bases");
            assertThat(t.get("dockerfiles").rows()).hasSize(6);
            assertThat(t.get("locations").rows()).hasSize(13);
            assertThat(t.get("archetypes").rows()).hasSize(3);
            assertThat(t.get("bases").rows()).hasSize(1);
            assertThat(column(t.get("dockerfiles"), "archetype_key"))
                    .containsExactly("sig-1", "sig-1", "sig-1", "sig-2", "parse-failures", "parse-failures");
            assertThat(column(t.get("dockerfiles"), "parse_error"))
                    .containsExactly(null, null, null, null, "Syntax error", "Syntax error");
        });
    }

    @Test
    void aDockerfilesRowHasItsBasePathWithoutHostAndItsEffectiveUser() {
        Dockerfile file = BaseCatalogueTest.dockerfile("a", new Base("registry.example.com/ubi9", "9", "sha"), 1);
        Estate estate = new Estate(List.of(file), List.of(), List.of(), Map.of("a", UserKind.NAMED));

        assertThat(Tables.dockerfiles(estate, Map.of("a", "k")).rows()).containsExactly(
                Arrays.asList("a", "sig", "ubi9", "9", "sha", "ROOT", "NAMED", 0, 0, "k", null));
    }

    @Test
    void everyLocationOfParsedAndFailedFilesIsARow() {
        Estate estate = new Estate(List.of(PassesTest.dockerfile("a", "t", "s", 1)),
                List.of(PassesTest.failure("bad", 1)), List.of(), Map.of());

        assertThat(Tables.locations(estate).rows()).containsExactly(
                List.of("org/repo-a-0", "Dockerfile", "RELEASED", "a"),
                List.of("org/repo-bad-0", "Dockerfile", "RELEASED", "bad"));
    }

    @Test
    void anArchetypesRowHasItsRankKeySizeSignatureAndRepresentative() {
        Ranking ranking = new Ranking(List.of(new Archetype(1, "k", 5, List.of("a", "b"), "sig", "a", List.of())),
                5, 1, 1);

        assertThat(Tables.archetypes(ranking).rows()).containsExactly(List.of(1, "k", 5, 2, "sig", "a"));
    }

    @Test
    void aBasesRowHasItsStatusAndEndOfLifeDate() {
        BaseRow base = new BaseRow("ubi9", null, "ubi9", 2, 1, "unknown", null);

        assertThat(Tables.bases(List.of(base)).rows())
                .containsExactly(Arrays.asList("ubi9", null, "ubi9", 2, 1, "unknown", null));
    }

    @Test
    void anAbsentEnumIsAnEmptyCell() {
        assertThat(Tables.name(UserKind.ROOT)).isEqualTo("ROOT");
        assertThat(Tables.name(null)).isNull();
    }

    private static List<Object> column(Table table, String name) {
        int index = table.columns().stream().map(Table.Column::name).toList().indexOf(name);
        return table.rows().stream().map(row -> row.get(index)).toList();
    }
}
