package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoggingTest {

    @Test
    void theLevelIsInfoAndTheFormatTextUnlessSaidOtherwise() {
        assertThat(Logging.of(List.of("survey"), Map.of())).isEqualTo(new Logging("info", false, null));
    }

    @Test
    void theEnvironmentSetsTheLevelTheFormatAndAConfigurationOfOnesOwn() {
        assertThat(Logging.of(List.of(), Map.of("LOG_LEVEL", "DEBUG", "LOG_FORMAT", "json", "LOG4J_CONFIG",
                "/etc/log4j2.xml"))).isEqualTo(new Logging("debug", true, "/etc/log4j2.xml"));
    }

    @Test
    void quietAndVerboseOnTheCommandLineWinOverTheEnvironment() {
        assertAll(
                () -> assertThat(Logging.of(List.of("--quiet", "facts"), Map.of("LOG_LEVEL", "debug")).level())
                        .isEqualTo("error"),
                () -> assertThat(Logging.of(List.of("facts", "--verbose"), Map.of()).level()).isEqualTo("debug"));
    }

    @Test
    void aLevelOrFormatThatIsNoneOfTheChoicesIsRefusedByName() {
        assertAll(
                () -> assertThatThrownBy(() -> Logging.of(List.of(), Map.of("LOG_LEVEL", "loud")))
                        .isInstanceOf(UsageError.class)
                        .hasMessage("LOG_LEVEL is error, warn, info or debug, not loud"),
                () -> assertThatThrownBy(() -> Logging.of(List.of(), Map.of("LOG_FORMAT", "xml")))
                        .isInstanceOf(UsageError.class).hasMessage("LOG_FORMAT is text or json, not xml"));
    }

    @Test
    void theFlagsAreTakenOutOfTheArgumentsTheCommandSees() {
        assertThat(Logging.withoutFlags(List.of("--verbose", "survey", "--quiet", "--try", "https://x/y")))
                .containsExactly("survey", "--try", "https://x/y");
    }

    @Test
    void theSettingsBecomeTheSystemPropertiesTheLog4jConfigurationReads() {
        assertAll(
                () -> assertThat(new Logging("warn", true, null).properties()).containsExactlyInAnyOrderEntriesOf(
                        Map.of(Logging.LEVEL, "warn", Logging.FORMAT, "json")),
                () -> assertThat(new Logging("info", false, "/my.xml").properties())
                        .containsEntry(Logging.FORMAT, "text").containsEntry("log4j2.configurationFile", "/my.xml"));
    }
}
