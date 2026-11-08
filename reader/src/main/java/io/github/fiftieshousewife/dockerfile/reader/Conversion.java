package io.github.fiftieshousewife.dockerfile.reader;

import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.Dockerfile;
import java.util.List;

/**
 * A Dockerfile read into the model, with the comments of each instruction (per stage, per instruction, its lines),
 * which the model itself does not hold.
 */
public record Conversion(Dockerfile model, List<List<List<String>>> comments) {

    public Conversion {
        comments = List.copyOf(comments.stream().map(stage -> List.copyOf(stage.stream().map(List::copyOf).toList()))
                .toList());
    }

    /** Every instruction kept as written, for review: what the model could not type. */
    public List<Raw> raw() {
        return model.stages().stream()
                .flatMap(stage -> stage.instructions().stream())
                .<Raw>mapMulti((instruction, raw) -> {
                    if (instruction instanceof Raw r) {
                        raw.accept(r);
                    }
                })
                .toList();
    }
}
