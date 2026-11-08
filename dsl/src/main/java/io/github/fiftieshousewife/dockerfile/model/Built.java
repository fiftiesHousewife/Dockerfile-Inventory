package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

/** A built image: its model, and every {@link Raw} it uses, so each escape from the model is reviewed. */
public record Built(Dockerfile dockerfile, List<Raw> raw) {

    public Built {
        raw = List.copyOf(raw);
    }
}
