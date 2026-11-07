package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableTest {

    private static final Table TABLE = new Table("bases",
            List.of(new Table.Column("path", Table.Type.TEXT), new Table.Column("locations", Table.Type.NUMBER),
                    new Table.Column("eol_date", Table.Type.DATE)),
            List.of(List.of("ubi9/ubi-minimal", 4, "2032-05-31"),
                    Arrays.asList("odd, \"quoted\"\nname", 1, null)));

    @Test
    void csvHasAHeaderAndOneRowPerRecordWithCommasQuotesAndNewlinesEscaped() {
        assertThat(TABLE.csv()).isEqualTo("""
                path,locations,eol_date\r
                ubi9/ubi-minimal,4,2032-05-31\r
                "odd, ""quoted""
                name",1,\r
                """);
    }

    @Test
    void aFieldIsQuotedOnlyWhenItHoldsACommaQuoteOrLineBreak() {
        assertThat(Table.field("plain")).isEqualTo("plain");
        assertThat(Table.field(null)).isEmpty();
        assertThat(Table.field(4)).isEqualTo("4");
        assertThat(Table.field("a\rb")).isEqualTo("\"a\rb\"");
        assertThat(Table.field("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    }

    @Test
    void theDdlCreatesTheTableWithEachColumnsOracleType() {
        assertThat(TABLE.ddl()).isEqualTo("""
                CREATE TABLE bases (
                    path VARCHAR2(4000),
                    locations NUMBER,
                    eol_date DATE
                );
                """);
    }
}
