package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Port;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Stage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class Convert {

    private static final String NOT_JSON = "exec form that is not valid JSON";

    private final Docker.File file;
    private final Printed printed;

    private Convert(Docker.File file) {
        this.file = file;
        this.printed = new Printed(file);
    }

    static Conversion of(Docker.File file) {
        return new Convert(file).convert();
    }

    record ConvertedStage(Stage stage, List<List<String>> comments) {
    }

    Conversion convert() {
        List<Instruction.Arg> globalArgs = file.getGlobalArgs().stream()
                .flatMap(arg -> ConvertPairs.args(arg).stream())
                .toList();
        Set<String> earlierStages = new HashSet<>();
        List<Stage> stages = new ArrayList<>();
        List<List<List<String>>> comments = new ArrayList<>();
        for (Docker.Stage stage : file.getStages()) {
            ConvertedStage converted = stage(stage, earlierStages);
            stages.add(converted.stage());
            comments.add(converted.comments());
            if (converted.stage().name() != null) {
                earlierStages.add(converted.stage().name());
            }
        }
        return new Conversion(new Dockerfile(globalArgs, stages), comments);
    }

    ConvertedStage stage(Docker.Stage stage, Set<String> earlierStages) {
        Docker.From from = stage.getFrom();
        Docker.From.As as = from.getAs();
        String name = as == null ? null : as.getName().getText();
        String image = Values.text(from.getImageName());
        boolean onStage = from.getTag() == null && from.getDigest() == null && earlierStages.contains(image);
        List<Instruction> instructions = new ArrayList<>();
        List<List<String>> comments = new ArrayList<>();
        for (Docker.Instruction instruction : stage.getInstructions()) {
            List<Instruction> converted = converted(instruction);
            instructions.addAll(converted);
            comments.add(printed.comments(instruction));
            converted.stream().skip(1).forEach(extra -> comments.add(List.of()));
        }
        Stage model = new Stage(name, onStage ? null : base(from, image), onStage ? image : null, instructions);
        return new ConvertedStage(model, comments);
    }

    static BaseImage base(Docker.From from, String image) {
        Docker.Argument tag = from.getTag();
        Docker.Argument digest = from.getDigest();
        return new BaseImage(image, tag == null ? null : Values.text(tag), digest == null ? null : Values.text(digest));
    }

    List<Instruction> converted(Docker.Instruction instruction) {
        return instruction instanceof Docker.Arg arg
                ? List.copyOf(ConvertPairs.args(arg)) : List.of(instruction(instruction));
    }

    Instruction instruction(Docker.Instruction instruction) {
        return switch (instruction) {
            case Docker.Env env -> ConvertPairs.env(printed.text(env));
            case Docker.Label label -> ConvertPairs.label(printed.text(label));
            case Docker.Workdir workdir -> new Instruction.Workdir(Values.unquote(Values.text(workdir.getPath())));
            case Docker.User user -> user(user);
            case Docker.Expose expose -> ports(expose).<Instruction>map(Instruction.Expose::new)
                    .orElseGet(() -> new Instruction.Raw(printed.text(expose),
                            "EXPOSE with a port that is not a number, range or variable"));
            case Docker.Run run -> ConvertCommands.run(run, printed.text(run));
            case Docker.Entrypoint entrypoint when entrypoint.getCommand() instanceof Docker.ExecForm exec ->
                    ConvertCommands.arguments(exec).<Instruction>map(Instruction.Entrypoint::new)
                            .orElseGet(() -> new Instruction.Raw(printed.text(entrypoint), NOT_JSON));
            case Docker.Cmd cmd when cmd.getCommand() instanceof Docker.ExecForm exec ->
                    ConvertCommands.arguments(exec).<Instruction>map(Instruction.Cmd::new)
                            .orElseGet(() -> new Instruction.Raw(printed.text(cmd), NOT_JSON));
            case Docker.Copy copy -> ConvertCopy.copy(printed.text(copy));
            case Docker.Entrypoint entrypoint -> new Instruction.Raw(printed.text(entrypoint), "shell-form ENTRYPOINT");
            case Docker.Cmd cmd -> new Instruction.Raw(printed.text(cmd), "shell-form CMD");
            case Docker.Shell shell -> ConvertMetadata.shell(shell, printed.text(shell));
            case Docker.Volume volume -> ConvertMetadata.volume(volume, printed.text(volume));
            case Docker.Stopsignal signal -> new Instruction.StopSignal(Values.text(signal.getSignal()).strip());
            case Docker.Maintainer maintainer -> new Instruction.Maintainer(Values.text(maintainer.getText()).strip());
            default -> notModelled(printed.text(instruction));
        };
    }

    static Instruction.Raw notModelled(String text) {
        String keyword = text.split("\\s", 2)[0].toUpperCase(Locale.ROOT);
        return new Instruction.Raw(text, keyword + " is not modelled");
    }

    static Instruction.User user(Docker.User user) {
        Docker.Argument group = user.getGroup();
        return new Instruction.User(Values.braced(Values.unquote(Values.text(user.getUser())))
                + (group == null ? "" : ":" + Values.braced(Values.unquote(Values.text(group)))));
    }

    static Optional<List<Port>> ports(Docker.Expose expose) {
        try {
            return Optional.of(expose.getPorts().stream().map(port -> new Port(port.getText().strip())).toList());
        } catch (IllegalArgumentException notAPort) {
            return Optional.empty();
        }
    }
}
