package io.github.fiftieshousewife.dockerfileinventory.validator;

final class Inheritance {

    private Inheritance() {
    }

    static boolean inherits(Check check, Subject child) {
        return switch (check) {
            case P1 -> child.facts().finalUser() == null;
            case P4, P6, P10 -> child.config().entrypoint() == null && child.config().cmd() == null;
            case P2 -> !WritablePaths.makesGroupWritable(child);
            case P3, P5, P9 -> true;
            case P7, P8, P11, D1, D2, D3, D4, D5, D6, D7 -> false;
            case S1, S2, S3, S4, S5, S6, S7, R1, R2, R3, R4, H1, H2, H3, H4, H5, H6, H7, H8, H9, I1, I2 -> false;
        };
    }
}
