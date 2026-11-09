package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlainAddTest {

    @Test
    void h5ReportsAnAddOfLocalFilesThatAreNoArchiveToUnpack() {
        assertThat(new PlainAdd().check(Subjects.of("""
                FROM ubi9
                ADD app.jar /opt/app.jar
                ADD rootfs.tar.gz /
                ADD https://example.com/tool /usr/bin/tool
                ADD ${DIST} /opt/
                ADD --chown=1001:0 ["conf/", "/etc/app/"]
                COPY app.jar /opt/
                """))).extracting(Finding::line, Finding::message).containsExactly(
                        tuple(2, "ADD of app.jar, which COPY would do"),
                        tuple(6, "ADD of conf/, which COPY would do"));
    }

    @Test
    void h5IgnoresAFileThatDidNotParse() {
        assertThat(new PlainAdd().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"rootfs.tar, true", "a.tar.gz, true", "a.TGZ, true", "a.tar.xz, true", "a.txz, true",
        "a.tar.bz2, true", "a.tbz2, true", "a.tar.zst, true", "app.jar, false", "a.gz, false", "conf/, false"})
    void anArchiveAddUnpacksIsATarCompressedOrNot(String source, boolean archive) {
        assertThat(PlainAdd.archive(source)).isEqualTo(archive);
    }
}
