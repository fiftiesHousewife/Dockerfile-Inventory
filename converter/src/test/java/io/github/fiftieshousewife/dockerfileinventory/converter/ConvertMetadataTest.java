package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.model.Copy;
import io.github.fiftieshousewife.dockerfile.model.Healthcheck;
import io.github.fiftieshousewife.dockerfile.model.Onbuild;
import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.Run;
import io.github.fiftieshousewife.dockerfile.model.Volume;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.model.CommandForm;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import io.github.fiftieshousewife.dockerfile.model.RunBody;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConvertMetadataTest {

    @Test
    void aShellWhoseJsonIsNotValidStaysRawAsDockerCannotReadItEither() {
        List<Instruction> instructions = Convert.of(Parsed.of("FROM ubi9\nSHELL [\"/bin/bash\", \"\\d\"]\n"))
                .model().stages().getFirst().instructions();

        assertThat(instructions).singleElement().isInstanceOf(Raw.class);
    }

    @Test
    void aVolumeInJsonFormDecodesItsEscapesAndOneInShellFormKeepsItsWords() {
        List<Instruction> instructions = Convert.of(Parsed.of("FROM ubi9\nVOLUME [\"/with space\"]\nVOLUME /a /b\n"))
                .model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(new Volume(List.of("/with space"), true),
                new Volume(List.of("/a", "/b"), false));
    }

    @Test
    void aShellFormVolumeKeepsEachWordAsWrittenQuotesIncluded() {
        List<Instruction> instructions = Convert.of(Parsed.of("FROM ubi9\nVOLUME \"/test_dir\" '/b' /c\n"))
                .model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(new Volume(List.of("\"/test_dir\"", "'/b'", "/c"), false));
    }

    @Test
    void aHealthcheckKeepsItsOptionsInOrderAndItsCommandsForm() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                HEALTHCHECK --interval=30s --timeout=3s CMD ["curl", "-f", "http://localhost/"]
                HEALTHCHECK CMD curl -f http://localhost/ \\
                    || exit 1
                HEALTHCHECK NONE
                """)).model().stages().getFirst().instructions();

        Map<String, String> options = new LinkedHashMap<>();
        options.put("interval", "30s");
        options.put("timeout", "3s");
        assertThat(instructions).containsExactly(
                new Healthcheck(options, List.of("curl", "-f", "http://localhost/"), CommandForm.EXEC),
                new Healthcheck(Map.of(), List.of("curl -f http://localhost/ || exit 1"),
                        CommandForm.SHELL),
                Healthcheck.NONE);
        assertThat(((Healthcheck) instructions.getFirst()).options().keySet())
                .containsExactly("interval", "timeout");
    }

    @Test
    void aHealthcheckWithAnOptionDockerLacksStaysRaw() {
        assertThat(Convert.of(Parsed.of("FROM ubi9\nHEALTHCHECK --every=5s CMD true\n")).model().stages().getFirst()
                .instructions()).singleElement().isInstanceOf(Raw.class);
    }

    @Test
    void anOnbuildHoldsItsConvertedInstructionUnlessThatStaysRaw() {
        List<Instruction> instructions = Convert.of(Parsed.of("""
                FROM ubi9
                ONBUILD COPY . /app/
                ONBUILD RUN make
                ONBUILD COPY --exclude=*.md . /src/
                """)).model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(
                new Onbuild(new Copy(List.of("."), "/app/", null, null)),
                new Onbuild(new Run(new RunBody.Shell(List.of("make")))),
                new Raw("ONBUILD COPY --exclude=*.md . /src/", "ONBUILD with COPY with flag --exclude"));
    }
}
