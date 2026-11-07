package io.github.fiftieshousewife.dockerfileinventory.converter;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageManager;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Port;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunFlag;
import java.util.List;
import java.util.Map;

final class BuilderCalls {

    private static final ClassName LIST = ClassName.get(List.class);
    private static final ClassName MAP = ClassName.get(Map.class);

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
            case Instruction.Add add -> CodeBlock.of(".add($L, $S, $S, $S, $S, $L, $L, $L)", list(add.sources()),
                    add.destination(), add.chown(), add.chmod(), add.checksum(), add.keepGitDir(), add.link(),
                    add.jsonForm());
            case Instruction.CopyHeredoc heredoc -> CodeBlock.of(".copyHeredoc($S, $S, $L, $L)", heredoc.destination(),
                    heredoc.delimiter(), heredoc.quoted(), list(heredoc.lines()));
            case Instruction.User user -> CodeBlock.of(".user($S)", user.user());
            case Instruction.Workdir workdir -> CodeBlock.of(".workdir($S)", workdir.path());
            case Instruction.Expose expose ->
                    CodeBlock.of(".expose($L)", strings(expose.ports().stream().map(Port::spec).toList()));
            case Instruction.Entrypoint entrypoint -> CodeBlock.of(".entrypoint($L)", strings(entrypoint.command()));
            case Instruction.Cmd cmd -> CodeBlock.of(".cmd($L)", strings(cmd.command()));
            case Instruction.Shell shell -> CodeBlock.of(".shell($L)", strings(shell.command()));
            case Instruction.Volume volume -> CodeBlock.of(".volume($L, $L)", list(volume.paths()), volume.jsonForm());
            case Instruction.StopSignal stopSignal -> CodeBlock.of(".stopSignal($S)", stopSignal.signal());
            case Instruction.Maintainer maintainer -> CodeBlock.of(".maintainer($S)", maintainer.name());
            case Instruction.Raw raw -> CodeBlock.of(".raw($S, $S)", raw.text(), raw.reason());
        };
    }

    static CodeBlock run(Instruction.Run run) {
        CodeBlock flags = run.flags().isEmpty() ? CodeBlock.of("") : CodeBlock.of("$L, ", flags(run));
        return switch (run.body()) {
            case RunBody.Install install -> CodeBlock.of(".install($L$T.$L, $L, $L)", flags, PackageManager.class,
                    install.install().manager().name(), list(install.install().flags()),
                    list(install.install().packages()));
            case RunBody.Shell shell -> CodeBlock.of(".run($L$L)", flags, strings(shell.commands()));
            case RunBody.Exec exec -> CodeBlock.of(".runExec($L, $L)", flags(run), strings(exec.arguments()));
            case RunBody.Heredoc heredoc -> CodeBlock.of(".runHeredoc($L, $S, $S, $L, $L)", flags(run),
                    heredoc.command(), heredoc.delimiter(), heredoc.quoted(), list(heredoc.lines()));
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
        return copy.chmod() == null && !copy.link() && !copy.jsonForm()
                ? CodeBlock.of(".copy($L, $S, $S, $S)", list(copy.sources()), copy.destination(), copy.chown(),
                        copy.fromStage())
                : CodeBlock.of(".copy($L, $S, $S, $S, $S, $L, $L)", list(copy.sources()), copy.destination(),
                        copy.chown(), copy.fromStage(), copy.chmod(), copy.link(), copy.jsonForm());
    }

    private static CodeBlock strings(List<String> values) {
        return values.stream().map(v -> CodeBlock.of("$S", v)).collect(CodeBlock.joining(", "));
    }

    static CodeBlock list(List<String> values) {
        return CodeBlock.of("$T.of($L)", LIST, strings(values));
    }

    private static CodeBlock map(Map<String, String> values) {
        CodeBlock entries = values.entrySet().stream()
                .map(e -> CodeBlock.of("$T.entry($S, $S)", MAP, e.getKey(), e.getValue()))
                .collect(CodeBlock.joining(", "));
        return CodeBlock.of("$T.ofEntries($L)", MAP, entries);
    }
}
