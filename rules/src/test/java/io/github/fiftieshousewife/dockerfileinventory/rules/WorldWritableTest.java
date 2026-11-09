package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorldWritableTest {

    @Test
    void s5ReportsAChmodInTheFinalImageThatLetsEveryoneWrite() {
        assertThat(new WorldWritable().check(Subjects.of("""
                FROM golang AS build
                RUN chmod 777 /src
                FROM ubi9
                RUN chmod 777 /data
                RUN chmod -R g=u /opt/app && chmod a+w /var/log/app
                RUN chmod 755 /usr/local/bin/app
                """))).extracting(Finding::line).containsExactly(4, 5);
    }

    @Test
    void s5IgnoresAFileThatDidNotParse() {
        assertThat(new WorldWritable().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"777, true", "0666, true", "1777, true", "o+w, true", "a+rwx, true", "go+w, true", "ugo=rwx, true",
        "755, false", "g=u, false", "g+w, false", "+w, false", "u+x, false", "o-w, false"})
    void everyoneMayWriteWhenTheModesOthersDigitHasTheWriteBitOrAClauseForOthersAddsWrite(String mode,
                                                                                           boolean everyone) {
        assertThat(WorldWritable.everyoneWrites(mode)).isEqualTo(everyone);
    }
}
