package io.github.fiftieshousewife.dockerfileinventory.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LogConfigurationTest {

    private static String logged(Logging logging, String message, boolean debug) {
        PrintStream stderr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        logging.properties().forEach(System::setProperty);
        LoggerContext context = Configurator.initialize("test-" + logging, "log4j2.xml");
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            if (debug) {
                context.getLogger("survey").debug(message);
            } else {
                context.getLogger("survey").info(message);
            }
            context.getLogger("org.openrewrite.Parser").info("noise");
        } finally {
            System.setErr(stderr);
            Configurator.shutdown(context);
            logging.properties().keySet().forEach(System::clearProperty);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    void aTextLineStartsWithItsTimeAndLevel() {
        assertThat(logged(new Logging("info", false, null), "survey: facts into out/a/facts", false))
                .matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}\\S+ INFO  survey: facts into out/a/facts\\n");
    }

    @Test
    void aJsonLineIsOneObjectWithTheTimeTheLevelAndTheMessage() {
        JsonNode line = new ObjectMapper().readTree(logged(new Logging("info", true, null), "done", false));

        assertAll(
                () -> assertThat(line.path("message").asString()).isEqualTo("done"),
                () -> assertThat(line.path("log.level").asString()).isEqualTo("INFO"),
                () -> assertThat(line.path("@timestamp").asString()).isNotEmpty());
    }

    @Test
    void aMessageBelowTheLevelIsLeftOut() {
        assertThat(logged(new Logging("info", false, null), "a call", true)).isEmpty();
    }

    @Test
    void debugShowsDebugButTheLibrariesStayQuietBelowWarn() {
        assertThat(logged(new Logging("debug", false, null), "a call", true)).contains("DEBUG a call")
                .doesNotContain("noise");
    }
}
