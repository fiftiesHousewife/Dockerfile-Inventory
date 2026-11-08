package io.github.fiftieshousewife.dockerfileinventory.validator;

// Read from the Dockerfile's instructions or the image's configuration, or inferred by a heuristic on text.
enum Certainty {
    READ, INFERRED
}
