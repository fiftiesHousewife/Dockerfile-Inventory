package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class UnverifiedDownloadTest {

    @Test
    void s6ReportsAnAddOfAUrlWithoutAChecksum() {
        assertThat(new UnverifiedDownload().check(Subjects.of("""
                FROM ubi9
                ADD https://example.com/tool.tar.gz /opt/
                ADD --checksum=sha256:24454f830cdb571e2c4ad15481119c43b3cafd48dd869a9b2945d1036d1dc68d \\
                    https://example.com/app.jar /opt/app.jar
                ADD --chown=1001:0 git@github.com:acme/app.git /src
                ADD app.tar.gz /opt/
                COPY https.txt /etc/
                """))).extracting(Finding::line, Finding::message).containsExactly(
                        tuple(2, "ADD downloads https://example.com/tool.tar.gz without a checksum"),
                        tuple(5, "ADD downloads git@github.com:acme/app.git without a checksum"));
    }

    @Test
    void s6IgnoresAFileThatDidNotParse() {
        assertThat(new UnverifiedDownload().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @Test
    void theUnverifiedSourcesAreTheRemoteOnesOfAnAddWithoutAChecksumInEitherForm() {
        assertAll(
                () -> assertThat(UnverifiedDownload.unverified("ADD [\"https://x/a.tgz\", \"b.txt\", \"/opt/\"]"))
                        .containsExactly("https://x/a.tgz"),
                () -> assertThat(UnverifiedDownload.unverified("ADD --chmod=755 \"http://x/run.sh\" /usr/bin/"))
                        .containsExactly("http://x/run.sh"),
                () -> assertThat(UnverifiedDownload.unverified("ADD --checksum=sha256:ab https://x/a /a")).isEmpty(),
                () -> assertThat(UnverifiedDownload.unverified("ADD https://x/only")).isEmpty());
    }
}
