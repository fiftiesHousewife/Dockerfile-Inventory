package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** One table for Oracle: its columns and rows, written as CSV for a load job (Q1). */
public record Table(String name, List<Column> columns, List<List<Object>> rows) {

    public record Column(String name, Type type) {
    }

    public enum Type {
        TEXT("VARCHAR2(4000)"),
        NUMBER("NUMBER"),
        DATE("DATE");

        private final String oracle;

        Type(String oracle) {
            this.oracle = oracle;
        }
    }

    public Table {
        columns = List.copyOf(columns);
        rows = rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
    }

    /** RFC 4180: CRLF line ends; a field holding a comma, quote or line break is quoted, quotes doubled. */
    public String csv() {
        StringBuilder csv = new StringBuilder();
        csv.append(columns.stream().map(Column::name).collect(Collectors.joining(","))).append("\r\n");
        rows.forEach(row -> csv.append(row.stream().map(Table::field).collect(Collectors.joining(","))).append("\r\n"));
        return csv.toString();
    }

    /** The {@code CREATE TABLE} statement a DBA runs once before loading the CSV. */
    public String ddl() {
        return columns.stream()
                .map(c -> "    " + c.name() + " " + c.type().oracle)
                .collect(Collectors.joining(",\n", "CREATE TABLE " + name + " (\n", "\n);\n"));
    }

    private static String field(Object value) {
        String text = value == null ? "" : value.toString();
        return text.matches("(?s).*[,\"\r\n].*") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }
}
