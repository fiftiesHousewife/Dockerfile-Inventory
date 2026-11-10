package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class FinalStage {

    private FinalStage() {
    }

    static List<Instruction> finished(List<Instruction> instructions, List<String> writable) {
        int lastUser = lastUser(instructions);
        return IntStream.range(0, instructions.size())
                .boxed()
                .flatMap(i -> i == lastUser && !writable.isEmpty()
                        ? Stream.of(groupZero(writable), instructions.get(i))
                        : finished(instructions.get(i)))
                .toList();
    }

    static int lastUser(List<Instruction> instructions) {
        return IntStream.iterate(instructions.size() - 1, i -> i >= 0, i -> i - 1)
                .filter(i -> instructions.get(i) instanceof User)
                .findFirst().orElse(-1);
    }

    static Stream<Instruction> finished(Instruction instruction) {
        return switch (instruction) {
            case Copy copy when copy.chown() == null -> Stream.of(new Copy(copy.sources(),
                    copy.destination(), "0:0", copy.fromStage(), copy.chmod(), copy.link(), copy.jsonForm()));
            default -> Stream.of(instruction);
        };
    }

    static Run groupZero(List<String> writable) {
        String paths = String.join(" ", writable);
        return new Run(new RunBody.Shell(List.of("chgrp -R 0 " + paths, "chmod -R g=u " + paths)));
    }
}
