package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class FinalImage {

    private FinalImage() {
    }

    static List<Docker.Instruction> instructions(Docker.File file) {
        List<Docker.Stage> stages = file.getStages();
        List<Docker.Instruction> instructions = new ArrayList<>();
        int index = stages.size() - 1;
        while (index >= 0) {
            Docker.Stage stage = stages.get(index);
            instructions.addAll(0, stage.getInstructions());
            index = earlierStage(stage.getFrom(), stages.subList(0, index));
        }
        return instructions;
    }

    static int earlierStage(Docker.From from, List<Docker.Stage> earlier) {
        if (from.getTag() != null || from.getDigest() != null) {
            return -1;
        }
        String name = from.getImageName().getContents().stream()
                .map(c -> c instanceof Docker.Literal l ? l.getText() : "$")
                .collect(Collectors.joining());
        return IntStream.iterate(earlier.size() - 1, i -> i >= 0, i -> i - 1)
                .filter(i -> named(earlier.get(i), name))
                .findFirst()
                .orElse(-1);
    }

    static boolean named(Docker.Stage stage, String name) {
        Docker.From.As as = stage.getFrom().getAs();
        return as != null && as.getName().getText().equals(name);
    }
}
