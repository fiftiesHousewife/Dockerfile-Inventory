package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class FindingsTable {

    static final String HEADER = "rule,severity,repository,path,blob_sha,line,message,value_hash,contributing\r\n";

    static final String DDL = """
            CREATE TABLE findings (
                rule VARCHAR2(4000),
                severity VARCHAR2(4000),
                repository VARCHAR2(4000),
                path VARCHAR2(4000),
                blob_sha VARCHAR2(4000),
                line NUMBER,
                message VARCHAR2(4000),
                value_hash VARCHAR2(4000),
                contributing VARCHAR2(4000)
            );
            """;

    private FindingsTable() {
    }

    static String csv(List<Finding> findings, Map<String, List<Location>> locations) {
        return findings.stream()
                .flatMap(f -> locations.getOrDefault(f.blobSha(), List.of()).stream().map(l -> row(f, l)))
                .collect(Collectors.joining("", HEADER, ""));
    }

    static String row(Finding f, Location l) {
        return Csv.row(f.rule(), f.severity().name(), l.repository(), l.path(), f.blobSha(), Csv.orEmpty(f.line()),
                f.message(), Csv.orEmpty(f.valueHash()), String.join(";", f.contributing()));
    }
}
