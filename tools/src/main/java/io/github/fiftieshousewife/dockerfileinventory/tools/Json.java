package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// The fixture bank's JSON: read line by line, written as jq printed the bank's earlier cases.
final class Json {

    static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DefaultPrettyPrinter AS_JQ = new JqPrinter();

    private Json() {
    }

    static Stream<JsonNode> rows(Path file) {
        try {
            return Files.readAllLines(file).stream().filter(line -> !line.isBlank()).map(MAPPER::readTree);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonNode read(Path file) {
        return MAPPER.readTree(file.toFile());
    }

    // jq's layout: two spaces, every array element on its own line, "name": value, and [] and {} when empty.
    static final class JqPrinter extends DefaultPrettyPrinter {

        private static final long serialVersionUID = 1L;

        JqPrinter() {
            super(Separators.createDefaultInstance().withObjectNameValueSpacing(Separators.Spacing.AFTER));
            indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withLinefeed("\n"));
            indentObjectsWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withLinefeed("\n"));
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new JqPrinter();
        }

        @Override
        public void writeEndArray(JsonGenerator g, int nrOfValues) {
            if (nrOfValues == 0) {
                _nesting--;
                g.writeRaw(']');
            } else {
                super.writeEndArray(g, nrOfValues);
            }
        }

        @Override
        public void writeEndObject(JsonGenerator g, int nrOfEntries) {
            if (nrOfEntries == 0) {
                _nesting--;
                g.writeRaw('}');
            } else {
                super.writeEndObject(g, nrOfEntries);
            }
        }
    }

    static String pretty(Object value) {
        return MAPPER.writer().with(AS_JQ).writeValueAsString(value) + "\n";
    }
}
