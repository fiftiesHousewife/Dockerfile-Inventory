package io.github.fiftieshousewife.dockerfile.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Draft {

    private final String name;
    private final BaseImage base;
    private final String fromStage;
    private final List<Instruction> instructions = new ArrayList<>();

    Draft(String name, BaseImage base, String fromStage) {
        this.name = name;
        this.base = base;
        this.fromStage = fromStage;
    }

    static Draft of(Stage stage) {
        Draft draft = new Draft(stage.name(), stage.base(), stage.fromStage());
        draft.instructions.addAll(stage.instructions());
        return draft;
    }

    void add(Instruction instruction) {
        instructions.add(instruction);
    }

    void onbuildLast() {
        if (instructions.isEmpty()) {
            throw new IllegalStateException("no instruction to run ONBUILD");
        }
        instructions.add(new Onbuild(instructions.removeLast()));
    }

    List<Instruction> instructions() {
        return List.copyOf(instructions);
    }

    Stage stage(List<Instruction> withInstructions) {
        return new Stage(name, base, fromStage, withInstructions);
    }

    Stage stage() {
        return stage(instructions);
    }

    String lastUser() {
        return instructions.stream()
                .filter(User.class::isInstance)
                .map(i -> ((User) i).user())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    BaseImage baseIn(List<Draft> stages) {
        Map<String, Draft> byName = stages.stream().filter(s -> s.name != null)
                .collect(Collectors.toMap(s -> s.name, Function.identity(), (first, second) -> second));
        Draft current = this;
        while (current.base == null) {
            current = byName.get(current.fromStage);
        }
        return current.base;
    }
}
