package io.github.fiftieshousewife.dockerfile.model;

/** {@code ONBUILD}: the instruction runs in images built {@code FROM} this one. */
public record Onbuild(Instruction instruction) implements Instruction {

    public Onbuild {
        if (instruction instanceof Onbuild || instruction instanceof Maintainer) {
            throw new IllegalArgumentException("ONBUILD cannot hold " + instruction);
        }
    }
}
