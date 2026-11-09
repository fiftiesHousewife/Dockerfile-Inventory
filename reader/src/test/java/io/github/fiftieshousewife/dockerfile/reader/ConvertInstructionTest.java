package io.github.fiftieshousewife.dockerfile.reader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.model.Cmd;
import io.github.fiftieshousewife.dockerfile.model.CommandForm;
import io.github.fiftieshousewife.dockerfile.model.Entrypoint;
import io.github.fiftieshousewife.dockerfile.model.Expose;
import io.github.fiftieshousewife.dockerfile.model.Onbuild;
import io.github.fiftieshousewife.dockerfile.model.Port;
import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.User;
import io.github.fiftieshousewife.dockerfile.model.Workdir;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

class ConvertInstructionTest {

    @Test
    void anInstructionTheModelDoesNotHaveIsRawNamingItsKeywordInCapitals() {
        assertThat(ConvertInstruction.notModelled("stopsignal SIGTERM"))
                .isEqualTo(new Raw("stopsignal SIGTERM", "STOPSIGNAL is not modelled"));
    }

    @Test
    void aUserAndGroupAreUnquotedWithTheirVariablesBraced() {
        Docker.User withGroup = (Docker.User) first("FROM ubi9\nUSER \"$UID\":'0'\n");
        Docker.User alone = (Docker.User) first("FROM ubi9\nUSER 1001\n");

        assertThat(List.of(ConvertInstruction.user(withGroup), ConvertInstruction.user(alone)))
                .containsExactly(new User("${UID}:0"), new User("1001"));
    }

    @Test
    void anExposeKeepsEachPortAsWrittenOrIsNotTypedWhenOneIsNotAPort() {
        assertThat(List.of("EXPOSE 8080", "EXPOSE 80 53/udp 8000-8010/tcp $PORT", "EXPOSE 80 ${PORT:-8080}"))
                .map(line -> ConvertInstruction.ports((Docker.Expose) first("FROM ubi9\n" + line + "\n")))
                .containsExactly(Optional.of(List.of(Port.of(8080))),
                        Optional.of(List.of(Port.of(80), new Port("53/udp"), new Port("8000-8010/tcp"),
                                new Port("$PORT"))),
                        Optional.empty());
    }

    @Test
    void anExposeOfPortsIsTypedAndOneWithAPortThatIsNotIsRaw() {
        assertThat(List.of("EXPOSE 8080", "EXPOSE ${PORT:-8080}"))
                .map(line -> converted("FROM ubi9\n" + line + "\n", (c, i) -> c.expose((Docker.Expose) i)))
                .containsExactly(new Expose(List.of(Port.of(8080))),
                        new Raw("EXPOSE ${PORT:-8080}", "EXPOSE with a port that is not a number, range or variable"));
    }

    @Test
    void anEntrypointInEitherFormIsTypedAndExecThatIsNotJsonIsRaw() {
        assertThat(List.of("ENTRYPOINT [\"run\"]", "ENTRYPOINT run now", "ENTRYPOINT [\"\\d\"]"))
                .map(line -> converted("FROM ubi9\n" + line + "\n", (c, i) -> c.entrypoint((Docker.Entrypoint) i)))
                .containsExactly(new Entrypoint(List.of("run")),
                        new Entrypoint(List.of("run now"), CommandForm.SHELL),
                        new Raw("ENTRYPOINT [\"\\d\"]", "exec form that is not valid JSON"));
    }

    @Test
    void aCmdInEitherFormIsTypedAndExecThatIsNotJsonIsRaw() {
        assertThat(List.of("CMD [\"serve\"]", "CMD serve now", "CMD [\"\\d\"]"))
                .map(line -> converted("FROM ubi9\n" + line + "\n", (c, i) -> c.cmd((Docker.Cmd) i)))
                .containsExactly(new Cmd(List.of("serve")),
                        new Cmd(List.of("serve now"), CommandForm.SHELL),
                        new Raw("CMD [\"\\d\"]", "exec form that is not valid JSON"));
    }

    @Test
    void anOnbuildWrapsItsInstructionOrIsRawWhenThatIsRawOrRefusedThere() {
        assertThat(List.of("ONBUILD WORKDIR /app", "ONBUILD EXPOSE ${P:-1}", "ONBUILD MAINTAINER me"))
                .map(line -> converted("FROM ubi9\n" + line + "\n", (c, i) -> c.onbuild((Docker.Onbuild) i)))
                .containsExactly(new Onbuild(new Workdir("/app")),
                        new Raw("ONBUILD EXPOSE ${P:-1}",
                                "ONBUILD with EXPOSE with a port that is not a number, range or variable"),
                        new Raw("ONBUILD MAINTAINER me", "ONBUILD with an instruction Docker refuses there"));
    }

    private interface Conversion {
        Object apply(ConvertInstruction convert, Docker.Instruction instruction);
    }

    private static Object converted(String dockerfile, Conversion conversion) {
        Docker.File file = Parsed.of(dockerfile);
        return conversion.apply(new ConvertInstruction(new Printed(file)),
                file.getStages().getFirst().getInstructions().getFirst());
    }

    private static Docker.Instruction first(String dockerfile) {
        return Parsed.of(dockerfile).getStages().getFirst().getInstructions().getFirst();
    }
}
