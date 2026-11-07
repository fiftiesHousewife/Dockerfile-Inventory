package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.CommandForm;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConvertMetadataTest {

    @Test
    void aShellWhoseJsonIsNotValidStaysRawAsDockerCannotReadItEither() {
        List<Instruction> instructions = Convert.of(Parsed.of("FROM ubi9\nSHELL [\"/bin/bash\", \"\\d\"]\n"))
                .model().stages().getFirst().instructions();

        assertThat(instructions).singleElement().isInstanceOf(Instruction.Raw.class);
    }

    @Test
    void aVolumeInJsonFormDecodesItsEscapesAndOneInShellFormKeepsItsWords() {
        List<Instruction> instructions = Convert.of(Parsed.of("FROM ubi9\nVOLUME [\"/with space\"]\nVOLUME /a /b\n"))
                .model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(new Instruction.Volume(List.of("/with space"), true),
                new Instruction.Volume(List.of("/a", "/b"), false));
    }

    @Test
    void aShellFormVolumeKeepsEachWordAsWrittenQuotesIncluded() {
        List<Instruction> instructions = Convert.of(Parsed.of("FROM ubi9\nVOLUME \"/test_dir\" '/b' /c\n"))
                .model().stages().getFirst().instructions();

        assertThat(instructions).containsExactly(new Instruction.Volume(List.of("\"/test_dir\"", "'/b'", "/c"), false));
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
                new Instruction.Healthcheck(options, List.of("curl", "-f", "http://localhost/"), CommandForm.EXEC),
                new Instruction.Healthcheck(Map.of(), List.of("curl -f http://localhost/ || exit 1"),
                        CommandForm.SHELL),
                Instruction.Healthcheck.NONE);
        assertThat(((Instruction.Healthcheck) instructions.getFirst()).options().keySet())
                .containsExactly("interval", "timeout");
    }

    @Test
    void aHealthcheckWithAnOptionDockerLacksStaysRaw() {
        assertThat(Convert.of(Parsed.of("FROM ubi9\nHEALTHCHECK --every=5s CMD true\n")).model().stages().getFirst()
                .instructions()).singleElement().isInstanceOf(Instruction.Raw.class);
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
                new Instruction.Onbuild(new Instruction.Copy(List.of("."), "/app/", null, null)),
                new Instruction.Onbuild(new Instruction.Run(new RunBody.Shell(List.of("make")))),
                new Instruction.Raw("ONBUILD COPY --exclude=*.md . /src/", "ONBUILD with COPY with flag --exclude"));
    }
}
