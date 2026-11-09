package io.github.fiftieshousewife.dockerfileinventory.validator;

// Whether a Dockerfile built on another keeps what a fix to that one changes: what an image takes from its base
// unless it sets its own.
final class Inheritance {

    private Inheritance() {
    }

    static boolean inherits(Check check, Subject child) {
        return switch (check) {
            case P1 -> child.facts().finalUser() == null;
            case P4, P6, P10 -> child.config().entrypoint() == null && child.config().cmd() == null;
            case P2 -> !WritablePaths.makesGroupWritable(child);
            case P3, P5, P9 -> true;
            case P7, P8 -> false;
            case S1, S2, S3, S4, R1, R2, R3, H1, H2, H3, I1, I2 -> false;
        };
    }
}
