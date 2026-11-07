package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Csv {

    private Csv() {
    }

    static String row(String... fields) {
        return Stream.of(fields).map(Csv::field).collect(Collectors.joining(",", "", "\r\n"));
    }

    static String field(String value) {
        return value.matches("(?s).*[,\"\r\n].*") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    static String orEmpty(Object value) {
        return Objects.toString(value, "");
    }
}
