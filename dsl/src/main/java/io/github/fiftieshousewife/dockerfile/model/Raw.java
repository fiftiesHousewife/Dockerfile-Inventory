package io.github.fiftieshousewife.dockerfile.model;

/** Text the model cannot type, rendered as written; the reason is kept because every use is reviewed. */
public record Raw(String text, String reason) implements Instruction {

    public Raw {
        Checks.notBlank(text, "raw text");
        Checks.notBlank(reason, "the reason for raw text");
    }
}
