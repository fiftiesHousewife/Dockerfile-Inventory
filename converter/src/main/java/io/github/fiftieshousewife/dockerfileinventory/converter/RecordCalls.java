package io.github.fiftieshousewife.dockerfileinventory.converter;

import com.palantir.javapoet.CodeBlock;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import io.github.fiftieshousewife.dockerfile.model.PackageInstall;
import io.github.fiftieshousewife.dockerfile.model.PackageManager;
import io.github.fiftieshousewife.dockerfile.model.RunBody;

final class RecordCalls {

    private RecordCalls() {
    }

    static CodeBlock copy(Instruction.Copy copy) {
        CodeBlock.Builder code = CodeBlock.builder().add("$T.of($L).to($S)", Instruction.Copy.class,
                BuilderCalls.strings(copy.sources()), copy.destination());
        if (copy.fromStage() != null) {
            code.add(".from($S)", copy.fromStage());
        }
        if (copy.chown() != null) {
            code.add(".ownedBy($S)", copy.chown());
        }
        if (copy.chmod() != null) {
            code.add(".mode($S)", copy.chmod());
        }
        return code.add(copy.link() ? ".linked()" : "").add(copy.jsonForm() ? ".inJsonForm()" : "").build();
    }

    static CodeBlock add(Instruction.Add add) {
        CodeBlock.Builder code = CodeBlock.builder().add("$T.of($L).to($S)", Instruction.Add.class,
                BuilderCalls.strings(add.sources()), add.destination());
        if (add.chown() != null) {
            code.add(".ownedBy($S)", add.chown());
        }
        if (add.chmod() != null) {
            code.add(".mode($S)", add.chmod());
        }
        if (add.checksum() != null) {
            code.add(".checksum($S)", add.checksum());
        }
        return code.add(add.keepGitDir() ? ".keepingGitDir()" : "").add(add.link() ? ".linked()" : "")
                .add(add.jsonForm() ? ".inJsonForm()" : "").build();
    }

    static CodeBlock copyHeredoc(Instruction.CopyHeredoc heredoc) {
        return CodeBlock.builder().add("$T.to($S).lines($L)", Instruction.CopyHeredoc.class, heredoc.destination(),
                        BuilderCalls.strings(heredoc.lines()))
                .add(heredocParts(heredoc.delimiter(), heredoc.quoted())).build();
    }

    static CodeBlock body(RunBody body) {
        return switch (body) {
            case RunBody.Install install -> CodeBlock.of("new $T(new $T($T.$L, $L, $L))", RunBody.Install.class,
                    PackageInstall.class, PackageManager.class, install.install().manager().name(),
                    BuilderCalls.list(install.install().flags()), BuilderCalls.list(install.install().packages()));
            case RunBody.Shell shell -> CodeBlock.of("new $T($L)", RunBody.Shell.class,
                    BuilderCalls.list(shell.commands()));
            case RunBody.Exec exec -> CodeBlock.of("new $T($L)", RunBody.Exec.class,
                    BuilderCalls.list(exec.arguments()));
            case RunBody.Heredoc heredoc -> CodeBlock.builder()
                    .add("$T.of($L)", RunBody.Heredoc.class, BuilderCalls.strings(heredoc.lines()))
                    .add(heredoc.command().isEmpty() ? CodeBlock.of("") : CodeBlock.of(".command($S)",
                            heredoc.command()))
                    .add(heredocParts(heredoc.delimiter(), heredoc.quoted())).build();
        };
    }

    static CodeBlock heredocParts(String delimiter, boolean quoted) {
        return CodeBlock.builder()
                .add(delimiter.equals("EOF") ? CodeBlock.of("") : CodeBlock.of(".delimitedBy($S)", delimiter))
                .add(quoted ? "" : ".expanding()").build();
    }
}
