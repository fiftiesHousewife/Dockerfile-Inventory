package io.github.fiftieshousewife.dockerfileinventory.converter;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import io.github.fiftieshousewife.dockerfileinventory.dsl.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.ImageBuilder;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PackageManager;
import io.github.fiftieshousewife.dockerfileinventory.dsl.PolicyViolation;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Port;
import io.github.fiftieshousewife.dockerfileinventory.dsl.RunBody;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Stage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.lang.model.element.Modifier;

final class JavaEmitter {

    private static final ClassName LIST = ClassName.get(List.class);
    private static final ClassName MAP = ClassName.get(Map.class);

    private JavaEmitter() {
    }

    static String emit(String packageName, String className, Conversion conversion,
                       List<PolicyViolation.Breach> breaches) {
        JavaFile.Builder file = JavaFile.builder(packageName, type(className, body(conversion)))
                .skipJavaLangImports(true);
        if (!breaches.isEmpty()) {
            file.addFileComment("$L", todos(breaches));
        }
        return file.build().toString();
    }

    static CodeBlock body(Conversion conversion) {
        CodeBlock.Builder code = CodeBlock.builder().add("return $T.create()", ImageBuilder.class).indent();
        Dockerfile model = conversion.model();
        model.globalArgs().forEach(arg -> code.add("\n.globalArg($S, $S)", arg.name(), arg.defaultValue()));
        IntStream.range(0, model.stages().size())
                .forEach(s -> code.add(stage(model.stages().get(s), conversion.comments().get(s))));
        return code.add("\n.model();\n").unindent().build();
    }

    static CodeBlock stage(Stage stage, List<List<String>> comments) {
        CodeBlock.Builder code = CodeBlock.builder().add(from(stage));
        IntStream.range(0, stage.instructions().size()).forEach(i -> {
            comments.get(i).forEach(comment -> code.add("\n// $L", comment));
            code.add("\n").add(call(stage.instructions().get(i)));
        });
        return code.build();
    }

    static CodeBlock from(Stage stage) {
        BaseImage base = stage.base();
        return base == null ? CodeBlock.of("\n.thenStageFrom($S, $S)", stage.name(), stage.fromStage())
                : CodeBlock.of("\n.thenStage($S, new $T($S, $S, $S))", stage.name(), BaseImage.class, base.name(),
                        base.tag(), base.digest());
    }

    static CodeBlock call(Instruction instruction) {
        return switch (instruction) {
            case Instruction.Arg arg -> arg.defaultValue() == null ? CodeBlock.of(".arg($S)", arg.name())
                    : CodeBlock.of(".arg($S, $S)", arg.name(), arg.defaultValue());
            case Instruction.Env env -> CodeBlock.of(".env($L)", map(env.values()));
            case Instruction.Label label -> CodeBlock.of(".label($L)", map(label.values()));
            case Instruction.Run run -> switch (run.body()) {
                case RunBody.Install install -> CodeBlock.of(".install($T.$L, $L, $L)", PackageManager.class,
                        install.install().manager().name(), list(install.install().flags()),
                        list(install.install().packages()));
                case RunBody.Shell shell -> CodeBlock.of(".run($L)", strings(shell.commands()));
            };
            case Instruction.Copy copy -> CodeBlock.of(".copy($L, $S, $S, $S)", list(copy.sources()),
                    copy.destination(), copy.chown(), copy.fromStage());
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

    static String todos(List<PolicyViolation.Breach> breaches) {
        return breaches.stream().map(b -> "TODO(" + b.rule() + "): " + b.message()).collect(Collectors.joining("\n"));
    }

    private static TypeSpec type(String className, CodeBlock code) {
        return TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Generated by the Dockerfile converter; regenerate rather than edit by hand.\n")
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                .addMethod(MethodSpec.methodBuilder("dockerfile")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(Dockerfile.class)
                        .addCode(code)
                        .build())
                .build();
    }

    private static CodeBlock strings(List<String> values) {
        return values.stream().map(v -> CodeBlock.of("$S", v)).collect(CodeBlock.joining(", "));
    }

    private static CodeBlock list(List<String> values) {
        return CodeBlock.of("$T.of($L)", LIST, strings(values));
    }

    private static CodeBlock map(Map<String, String> values) {
        CodeBlock entries = values.entrySet().stream()
                .map(e -> CodeBlock.of("$T.entry($S, $S)", MAP, e.getKey(), e.getValue()))
                .collect(CodeBlock.joining(", "));
        return CodeBlock.of("$T.ofEntries($L)", MAP, entries);
    }
}
