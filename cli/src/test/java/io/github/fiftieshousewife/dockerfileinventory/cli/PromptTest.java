package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfileinventory.discovery.FirstPass;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTest {

    private static final FirstPass PASS = new FirstPass(42, 37, 118, 96, 260, "out/payments-billing/discovery");

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private boolean answered(String answer) {
        return new Prompt(new PrintStream(out, true, StandardCharsets.UTF_8), () -> answer,
                Map.of("GHE_MAX_REQUESTS_PER_SECOND", "2"))
                .proceed(PASS);
    }

    @Test
    void theFirstPassIsSummedUpWithTheLikelyTimeThenAsked() {
        answered("y");

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("""
                42 repositories selected, 37 with Dockerfiles: 118 Dockerfiles, 96 distinct
                260 more calls to GitHub at 2 a second, then the other stages: about 3 minutes
                output in out/payments-billing
                Continue? [y/N]\s""");
    }

    @Test
    void onlyYesGoesOn() {
        assertAll(
                () -> assertThat(answered("y")).isTrue(),
                () -> assertThat(answered(" YES ")).isTrue(),
                () -> assertThat(answered("")).isFalse(),
                () -> assertThat(answered("n")).isFalse(),
                () -> assertThat(answered(null)).isFalse());
    }

    @Test
    void theLikelyTimeIsGitHubsCallsAtItsRateAndTheStagesPerDockerfile() {
        assertAll(
                () -> assertThat(Prompt.likely(PASS, 2)).isEqualTo(Duration.ofSeconds(130 + 10 + 1)),
                () -> assertThat(Prompt.inWords(Duration.ofSeconds(40))).isEqualTo("under a minute"),
                () -> assertThat(Prompt.inWords(Duration.ofSeconds(141))).isEqualTo("about 3 minutes"),
                () -> assertThat(Prompt.inWords(Duration.ofMinutes(150))).isEqualTo("about 2.5 hours"));
    }

    @Test
    void askedWithYesItGoesOnWithoutAsking() {
        assertThat(Prompt.confirmation(Map.of("ASSUME_YES", "true"), false, System.out).proceed(PASS)).isTrue();
    }

    @Test
    void withNoTerminalToAskOnAndNoYesItIsAUsageError() {
        assertThatThrownBy(() -> Prompt.confirmation(Map.of(), false, System.out))
                .isInstanceOf(UsageError.class)
                .hasMessage("no terminal to ask on: give --yes to go on without asking");
    }

    @Test
    void aNumberIsShownWholeOrToOnePlace() {
        assertAll(
                () -> assertThat(Prompt.number(2.0)).isEqualTo("2"),
                () -> assertThat(Prompt.number(2.46)).isEqualTo("2.5"));
    }
}
