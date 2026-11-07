package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

record Table(String name, List<Column> columns, List<List<Object>> rows) {
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");


    record Column(String name, Type type) {
    }

    enum Type {
        TEXT("VARCHAR2(4000)"),
        NUMBER("NUMBER"),
        DATE("DATE");

        private final String oracle;

        Type(String oracle) {
            this.oracle = oracle;
        }
    }

    Table {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList());
    }

    // RFC 4180, which asks for CRLF line ends.
    String csv() {
        StringBuilder csv = new StringBuilder();
        csv.append(columns.stream().map(Column::name).collect(Collectors.joining(","))).append("\r\n");
        rows.forEach(row -> csv.append(row.stream().map(Table::field).collect(Collectors.joining(","))).append("\r\n"));
        return csv.toString();
    }

    String ddl() {
        return columns.stream()
                .map(c -> "    " + c.name() + " " + c.type().oracle)
                .collect(Collectors.joining(",\n", "CREATE TABLE " + name + " (\n", "\n);\n"));
    }

    // A control character (a binary file's bytes quoted in a parser message, say) would make the file unloadable text.
    static String field(Object value) {
        String text = value == null ? "" : CONTROL.matcher(value.toString()).replaceAll("\uFFFD");
        return text.matches("(?s).*[,\"\r\n].*") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }
}
