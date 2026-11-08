package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.Dockerfile;
import java.util.List;

record Conversion(Dockerfile model, List<List<List<String>>> comments) {

    Conversion {
        comments = List.copyOf(comments.stream().map(stage -> List.copyOf(stage.stream().map(List::copyOf).toList()))
                .toList());
    }

    List<Raw> raw() {
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
