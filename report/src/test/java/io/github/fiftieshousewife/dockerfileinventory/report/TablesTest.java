package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(tables).containsOnlyKeys("dockerfiles", "locations", "archetypes", "bases");
        assertThat(tables.get("dockerfiles").rows()).hasSize(6);
        assertThat(tables.get("locations").rows()).hasSize(13);
        assertThat(tables.get("archetypes").rows()).hasSize(3);
        assertThat(tables.get("bases").rows()).hasSize(1);
        assertThat(column(tables.get("dockerfiles"), "archetype_key"))
                .containsExactly("sig-1", "sig-1", "sig-1", "sig-2", "parse-failures", "parse-failures");
        assertThat(column(tables.get("dockerfiles"), "parse_error"))
                .containsExactly(null, null, null, null, "Syntax error", "Syntax error");
    }

    private static List<Object> column(Table table, String name) {
        int index = table.columns().stream().map(Table.Column::name).toList().indexOf(name);
        return table.rows().stream().map(row -> row.get(index)).toList();
    }
}
