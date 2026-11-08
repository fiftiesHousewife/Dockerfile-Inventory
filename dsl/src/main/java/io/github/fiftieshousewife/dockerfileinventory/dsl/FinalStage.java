package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class FinalStage {

    private FinalStage() {
    }

    static List<Instruction> finished(List<Instruction> instructions, String user, List<String> writable) {
        int lastUser = lastUser(instructions);
        return IntStream.range(0, instructions.size())
                .boxed()
                .flatMap(i -> i == lastUser && !writable.isEmpty()
                        ? Stream.of(groupZero(writable), instructions.get(i))
                        : finished(instructions.get(i), user, writable))
                .toList();
    }

    // Before the last USER only: an earlier one (USER 0 to build) comes before the paths exist.
    static int lastUser(List<Instruction> instructions) {
        return IntStream.iterate(instructions.size() - 1, i -> i >= 0, i -> i - 1)
                .filter(i -> instructions.get(i) instanceof Instruction.User)
                .findFirst().orElse(-1);
    }

    static Stream<Instruction> finished(Instruction instruction, String user, List<String> writable) {
        return switch (instruction) {
            case Instruction.Copy copy when copy.chown() == null -> Stream.of(new Instruction.Copy(copy.sources(),
                    copy.destination(), user + ":0", copy.fromStage(), copy.chmod(), copy.link(), copy.jsonForm()));
            default -> Stream.of(instruction);
        };
    }

    static Instruction.Run groupZero(List<String> writable) {
        String paths = String.join(" ", writable);
        return new Instruction.Run(new RunBody.Shell(List.of("chgrp -R 0 " + paths, "chmod -R g=u " + paths)));
    }
}
