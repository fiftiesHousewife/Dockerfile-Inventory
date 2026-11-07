package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;
import java.util.stream.Stream;

final class FinalStage {

    private FinalStage() {
    }

    static List<Instruction> finished(List<Instruction> instructions, String user, List<String> writable) {
        return instructions.stream().flatMap(instruction -> finished(instruction, user, writable)).toList();
    }

    static Stream<Instruction> finished(Instruction instruction, String user, List<String> writable) {
        return switch (instruction) {
            case Instruction.User u when !writable.isEmpty() -> Stream.of(groupZero(writable), u);
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
