package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.model.Add;
import io.github.fiftieshousewife.dockerfile.model.Arg;
import io.github.fiftieshousewife.dockerfile.model.Cmd;
import io.github.fiftieshousewife.dockerfile.model.Copy;
import io.github.fiftieshousewife.dockerfile.model.CopyHeredoc;
import io.github.fiftieshousewife.dockerfile.model.Entrypoint;
import io.github.fiftieshousewife.dockerfile.model.Env;
import io.github.fiftieshousewife.dockerfile.model.Expose;
import io.github.fiftieshousewife.dockerfile.model.Healthcheck;
import io.github.fiftieshousewife.dockerfile.model.Label;
import io.github.fiftieshousewife.dockerfile.model.Maintainer;
import io.github.fiftieshousewife.dockerfile.model.Onbuild;
import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.Run;
import io.github.fiftieshousewife.dockerfile.model.Shell;
import io.github.fiftieshousewife.dockerfile.model.StopSignal;
import io.github.fiftieshousewife.dockerfile.model.User;
import io.github.fiftieshousewife.dockerfile.model.Volume;
import io.github.fiftieshousewife.dockerfile.model.Workdir;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.github.fiftieshousewife.dockerfile.model.CommandForm;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import io.github.fiftieshousewife.dockerfile.model.OrderedMap;
import io.github.fiftieshousewife.dockerfile.model.PackageManager;
import io.github.fiftieshousewife.dockerfile.model.Port;
import io.github.fiftieshousewife.dockerfile.model.RunBody;
import io.github.fiftieshousewife.dockerfile.model.RunFlag;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class BuilderCalls {

    private static final ClassName LIST = ClassName.get(List.class);

    private BuilderCalls() {
    }

    static CodeBlock call(Instruction instruction) {
        return switch (instruction) {
            case Arg arg -> arg.defaultValue() == null ? CodeBlock.of(".arg($S)", arg.name())
                    : CodeBlock.of(".arg($S, $S)", arg.name(), arg.defaultValue());
            case Env env -> CodeBlock.of(".env($L)", map(env.values()));
            case Label label -> CodeBlock.of(".label($L)", map(label.values()));
            case Run run -> run(run);
            case Copy copy -> copy(copy);
            case Add add -> CodeBlock.of(".add($L)", RecordCalls.add(add));
            case CopyHeredoc heredoc -> CodeBlock.of(".copyHeredoc($L)", RecordCalls.copyHeredoc(heredoc));
            case User user -> CodeBlock.of(".user($S)", user.user());
            case Workdir workdir -> CodeBlock.of(".workdir($S)", workdir.path());
            case Expose expose ->
                    CodeBlock.of(".expose($L)", strings(expose.ports().stream().map(Port::spec).toList()));
            case Entrypoint entrypoint -> command("entrypoint", entrypoint.command(), entrypoint.form());
            case Cmd cmd -> command("cmd", cmd.command(), cmd.form());
            case Shell shell -> CodeBlock.of(".shell($L)", strings(shell.command()));
            case Volume volume -> CodeBlock.of(".volume($L, $L)", list(volume.paths()), volume.jsonForm());
            case StopSignal stopSignal -> CodeBlock.of(".stopSignal($S)", stopSignal.signal());
            case Maintainer maintainer -> CodeBlock.of(".maintainer($S)", maintainer.name());
            case Healthcheck healthcheck -> healthcheck.command().isEmpty()
                    ? CodeBlock.of(".healthcheckNone()")
                    : CodeBlock.of(".healthcheck($L, $L, $T.$L)", map(healthcheck.options()),
                            list(healthcheck.command()), CommandForm.class, healthcheck.form().name());
            case Onbuild onbuild -> CodeBlock.of("$L.asOnbuild()", call(onbuild.instruction()));
            case Raw raw -> CodeBlock.of(".raw($S, $S)", raw.text(), raw.reason());
        };
    }

    static CodeBlock command(String method, List<String> command, CommandForm form) {
        return form == CommandForm.EXEC ? CodeBlock.of(".$L($L)", method, strings(command))
                : CodeBlock.of(".$L($L, $T.$L)", method, list(command), CommandForm.class, form.name());
    }

    static CodeBlock run(Run run) {
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

    static CodeBlock flags(Run run) {
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

    static CodeBlock copy(Copy copy) {
        return copy.sources().size() == 1
                && copy.equals(Copy.of(copy.sources().getFirst()).to(copy.destination()))
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
