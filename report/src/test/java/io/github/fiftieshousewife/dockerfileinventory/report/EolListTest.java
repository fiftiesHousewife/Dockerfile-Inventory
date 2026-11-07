package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EolListTest {

    @Test
    void anEntryIsReadWithItsFieldsStrippedAndAnOptionalDate() {
        assertThat(EolList.entry(" ubi9 , 9 , eol , 2026-01-01"))
                .isEqualTo(new EolList.Entry("ubi9", "9", "eol", LocalDate.parse("2026-01-01")));
        assertThat(EolList.entry("ubi9,9,supported,")).isEqualTo(new EolList.Entry("ubi9", "9", "supported", null));
        assertThat(EolList.entry("ubi9,9,supported")).isEqualTo(new EolList.Entry("ubi9", "9", "supported", null));
    }

    @Test
    void aPrefixCoversItsOwnTagAndTagsThatContinueAtAVersionBoundary() {
        assertThat(EolList.covers("", "anything")).isTrue();
        assertThat(EolList.covers("9", "9")).isTrue();
        assertThat(EolList.covers("9", "9.4")).isTrue();
        assertThat(EolList.covers("9", "9-1100")).isTrue();
        assertThat(EolList.covers("9", "91")).isFalse();
    }

    @Test
    void theLongestCoveringPrefixForThePathWinsAndAMissingTagMatchesOnlyAnEmptyPrefix() {
        EolList list = EolList.read(Path.of("src/test/resources/fixtures/eol.csv"));

        assertThat(list.lookup("ubi9/ubi-minimal", "9.2-1100")).map(EolList.Entry::status).contains("eol");
        assertThat(list.lookup("ubi9/ubi-minimal", null)).isEmpty();
        assertThat(EolList.empty().lookup("ubi9/ubi-minimal", "9.4")).isEmpty();
    }
}
