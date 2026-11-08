package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** {@code COPY}; {@code chown}, {@code fromStage} and {@code chmod} are optional. */
public record Copy(List<String> sources, String destination, String chown, String fromStage, String chmod, boolean link,
        boolean jsonForm) implements Instruction {

    public Copy {
        sources = List.copyOf(sources);
        Checks.notBlank(destination, "a COPY destination");
    }

    public Copy(List<String> sources, String destination, String chown, String fromStage) {
        this(sources, destination, chown, fromStage, null, false, false);
    }

    /** {@code Copy.of("a", "b").to("/dst/")}, then any with-method. */
    public static Sources of(String... sources) {
        return new Sources(List.of(sources));
    }

    public Copy from(String stage) {
        return new Copy(sources, destination, chown, stage, chmod, link, jsonForm);
    }

    public Copy ownedBy(String owner) {
        return new Copy(sources, destination, owner, fromStage, chmod, link, jsonForm);
    }

    public Copy mode(String mode) {
        return new Copy(sources, destination, chown, fromStage, mode, link, jsonForm);
    }

    public Copy linked() {
        return new Copy(sources, destination, chown, fromStage, chmod, true, jsonForm);
    }

    public Copy inJsonForm() {
        return new Copy(sources, destination, chown, fromStage, chmod, link, true);
    }

    /** What a {@code COPY} copies, waiting for where to. */
    public record Sources(List<String> sources) {

        public Sources {
            sources = List.copyOf(sources);
        }

        public Copy to(String destination) {
            return new Copy(sources, destination, null, null);
        }
    }
}
