package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class FinalImage {

    private FinalImage() {
    }

    static List<Docker.Instruction> instructions(Docker.File file) {
        List<Docker.Stage> stages = file.getStages();
        return chain(stages).reversed().stream().flatMap(index -> stages.get(index).getInstructions().stream())
                .toList();
    }

    static List<Integer> chain(List<Docker.Stage> stages) {
        return Stream.iterate(stages.size() - 1, index -> index >= 0,
                index -> earlierStage(stages.get(index).getFrom(), stages.subList(0, index))).toList();
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
