package io.github.fiftieshousewife.dockerfile.reader;

import io.github.fiftieshousewife.dockerfile.reader.internal.Values;
import io.github.fiftieshousewife.dockerfile.model.Arg;
import io.github.fiftieshousewife.dockerfile.model.BaseImage;
import io.github.fiftieshousewife.dockerfile.model.Dockerfile;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import io.github.fiftieshousewife.dockerfile.model.Stage;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class Convert {

    private final Docker.File file;
    private final Printed printed;
    private final ConvertInstruction instructions;

    private Convert(Docker.File file) {
        this.file = file;
        this.printed = new Printed(file);
        this.instructions = new ConvertInstruction(printed);
    }

    static Conversion of(Docker.File file) {
        return new Convert(file).convert();
    }

    record ConvertedStage(Stage stage, List<List<String>> comments) {
    }

    record ConvertedInstruction(List<Instruction> instructions, List<List<String>> comments) {
    }

    Conversion convert() {
        List<Arg> globalArgs = file.getGlobalArgs().stream()
                .flatMap(arg -> ConvertPairs.args(arg).stream())
                .toList();
        List<Docker.Stage> stages = file.getStages();
        List<String> names = stages.stream().map(Convert::name).toList();
        List<ConvertedStage> converted = IntStream.range(0, stages.size())
                .mapToObj(i -> stage(stages.get(i), earlierStages(names, i)))
                .toList();
        return new Conversion(new Dockerfile(globalArgs, converted.stream().map(ConvertedStage::stage).toList()),
                converted.stream().map(ConvertedStage::comments).toList());
    }

    static String name(Docker.Stage stage) {
        Docker.From.As as = stage.getFrom().getAs();
        return as == null ? null : as.getName().getText();
    }

    static Set<String> earlierStages(List<String> names, int stage) {
        return names.subList(0, stage).stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    ConvertedStage stage(Docker.Stage stage, Set<String> earlierStages) {
        Docker.From from = stage.getFrom();
        String image = Values.text(from.getImageName());
        boolean onStage = from.getTag() == null && from.getDigest() == null && earlierStages.contains(image);
        List<ConvertedInstruction> converted = stage.getInstructions().stream().map(this::converted).toList();
        Stage model = new Stage(name(stage), onStage ? null : base(from, image), onStage ? image : null,
                converted.stream().flatMap(c -> c.instructions().stream()).toList());
        return new ConvertedStage(model, converted.stream().flatMap(c -> c.comments().stream()).toList());
    }

    static BaseImage base(Docker.From from, String image) {
        Docker.Argument tag = from.getTag();
        Docker.Argument digest = from.getDigest();
        return new BaseImage(image, tag == null ? null : Values.text(tag), digest == null ? null : Values.text(digest));
    }

    ConvertedInstruction converted(Docker.Instruction instruction) {
        List<Instruction> converted = instruction instanceof Docker.Arg arg
                ? List.copyOf(ConvertPairs.args(arg)) : List.of(instructions.instruction(instruction));
        return new ConvertedInstruction(converted, Stream.concat(Stream.of(printed.comments(instruction)),
                converted.stream().skip(1).map(extra -> List.<String>of())).toList());
    }
}
