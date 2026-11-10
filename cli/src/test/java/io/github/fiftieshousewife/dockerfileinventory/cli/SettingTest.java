package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingTest {

    @Test
    void aRequiredSettingIsAlwaysRequiredAndAnOptionalOneNever() {
        assertAll(
                () -> assertThat(Setting.required("A", "a").requiredIn(Map.of())).isTrue(),
                () -> assertThat(Setting.optional("A", "a").requiredIn(Map.of("A", "x"))).isFalse());
    }

    @Test
    void aSettingRequiredWithAnotherIsRequiredOnceThatOneIsSet() {
        Setting rate = Setting.requiredWith("RATE", "URL", "calls a second");

        assertAll(
                () -> assertThat(rate.requiredIn(Map.of("URL", "https://a"))).isTrue(),
                () -> assertThat(rate.requiredIn(Map.of("URL", " "))).isFalse());
    }

    @Test
    void aSettingRequiredUnlessOthersIsRequiredOnlyWhenNoneIsSet() {
        Setting token = Setting.requiredUnless("TOKEN", List.of("TOKEN_FILE", "APP_ID"), "a token");

        assertAll(
                () -> assertThat(token.requiredIn(Map.of())).isTrue(),
                () -> assertThat(token.requiredIn(Map.of("APP_ID", "1"))).isFalse());
    }

    @Test
    void aSettingIsMissingWhenRequiredAndUnset() {
        Setting out = Setting.required("OUT_DIR", "the output folder");

        assertAll(
                () -> assertThat(out.missingFrom(Map.of("OUT_DIR", ""))).isTrue(),
                () -> assertThat(out.missingFrom(Map.of("OUT_DIR", "out"))).isFalse());
    }

    @Test
    void aValueIsUnsetWhenMissingOrBlank() {
        assertAll(
                () -> assertThat(Setting.unset(null)).isTrue(),
                () -> assertThat(Setting.unset(" ")).isTrue(),
                () -> assertThat(Setting.unset("x")).isFalse());
    }
}
