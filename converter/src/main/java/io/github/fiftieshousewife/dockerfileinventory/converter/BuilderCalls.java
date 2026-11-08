package io.github.fiftieshousewife.dockerfileinventory.converter;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.github.fiftieshousewife.dockerfileinventory.dsl.CommandForm;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.OrderedMap;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageManager;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Port;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunFlag;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class BuilderCalls {

    private static final ClassName LIST = ClassName.get(List.class);

    private BuilderCalls() {
    }

    static CodeBlock call(Instruction instruction) {
        return switch (instruction) {
            case Instruction.Arg arg -> arg.defaultValue() == null ? CodeBlock.of(".arg($S)", arg.name())
                    : CodeBlock.of(".arg($S, $S)", arg.name(), arg.defaultValue());
            case Instruction.Env env -> CodeBlock.of(".env($L)", map(env.values()));
            case Instruction.Label label -> CodeBlock.of(".label($L)", map(label.values()));
            case Instruction.Run run -> run(run);
            case Instruction.Copy copy -> copy(copy);
            case Instruction.Add add -> CodeBlock.of(".add($L)", RecordCalls.add(add));
            case Instruction.CopyHeredoc heredoc -> CodeBlock.of(".copyHeredoc($L)", RecordCalls.copyHeredoc(heredoc));
            case Instruction.User user -> CodeBlock.of(".user($S)", user.user());
            case Instruction.Workdir workdir -> CodeBlock.of(".workdir($S)", workdir.path());
            case Instruction.Expose expose ->
                    CodeBlock.of(".expose($L)", strings(expose.ports().stream().map(Port::spec).toList()));
            case Instruction.Entrypoint entrypoint -> command("entrypoint", entrypoint.command(), entrypoint.form());
            case Instruction.Cmd cmd -> command("cmd", cmd.command(), cmd.form());
            case Instruction.Shell shell -> CodeBlock.of(".shell($L)", strings(shell.command()));
            case Instruction.Volume volume -> CodeBlock.of(".volume($L, $L)", list(volume.paths()), volume.jsonForm());
            case Instruction.StopSignal stopSignal -> CodeBlock.of(".stopSignal($S)", stopSignal.signal());
            case Instruction.Maintainer maintainer -> CodeBlock.of(".maintainer($S)", maintainer.name());
            case Instruction.Healthcheck healthcheck -> healthcheck.command().isEmpty()
                    ? CodeBlock.of(".healthcheckNone()")
                    : CodeBlock.of(".healthcheck($L, $L, $T.$L)", map(healthcheck.options()),
                            list(healthcheck.command()), CommandForm.class, healthcheck.form().name());
            case Instruction.Onbuild onbuild -> CodeBlock.of("$L.asOnbuild()", call(onbuild.instruction()));
            case Instruction.Raw raw -> CodeBlock.of(".raw($S, $S)", raw.text(), raw.reason());
        };
    }

    static CodeBlock command(String method, List<String> command, CommandForm form) {
        return form == CommandForm.EXEC ? CodeBlock.of(".$L($L)", method, strings(command))
                : CodeBlock.of(".$L($L, $T.$L)", method, list(command), CommandForm.class, form.name());
    }

    static CodeBlock run(Instruction.Run run) {
        if (!run.flags().isEmpty()) {
            return run.body() instanceof RunBody.Shell shell
                    ? CodeBlock.of(".run($L, $L)", flags(run), strings(shell.commands()))
                    : CodeBlock.of(".run($L, $L)", flags(run), RecordCalls.body(run.body()));
        }
        return switch (run.body()) {
            case RunBody.Install install -> CodeBlock.of(".install($T.$L, $L, $L)", PackageManager.class,
                    install.install().manager().name(), list(install.install().flags()),
                    list(install.install().packages()));
            case RunBody.Shell shell -> CodeBlock.of(".run($L)", strings(shell.commands()));
            case RunBody.Exec exec -> CodeBlock.of(".runExec($L)", strings(exec.arguments()));
            case RunBody.Heredoc heredoc -> CodeBlock.of(".run($T.of(), $L)", LIST, RecordCalls.body(heredoc));
        };
    }

    static CodeBlock flags(Instruction.Run run) {
        return CodeBlock.of("$T.of($L)", LIST, CodeBlock.join(run.flags().stream().map(BuilderCalls::flag).toList(),
                ", "));
    }

    static CodeBlock flag(RunFlag flag) {
        return switch (flag) {
            case RunFlag.Mount mount -> CodeBlock.of("$T.parse($S)", RunFlag.Mount.class, mount.spec());
            case RunFlag.Network network -> CodeBlock.of("new $T($S)", RunFlag.Network.class, network.mode());
            case RunFlag.Security security -> CodeBlock.of("new $T($S)", RunFlag.Security.class, security.mode());
        };
    }

    static CodeBlock copy(Instruction.Copy copy) {
        return copy.sources().size() == 1
                && copy.equals(Instruction.Copy.of(copy.sources().getFirst()).to(copy.destination()))
                ? CodeBlock.of(".copy($S, $S)", copy.sources().getFirst(), copy.destination())
                : CodeBlock.of(".copy($L)", RecordCalls.copy(copy));
    }

    static CodeBlock strings(List<String> values) {
        return values.stream().map(v -> CodeBlock.of("$S", v)).collect(CodeBlock.joining(", "));
    }

    static CodeBlock list(List<String> values) {
        return CodeBlock.of("$T.of($L)", LIST, strings(values));
    }

    static CodeBlock map(Map<String, String> values) {
        return CodeBlock.of("$T.of($L)", OrderedMap.class, strings(values.entrySet().stream()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue())).toList()));
    }
}
