package io.github.fiftieshousewife.dockerfileinventory.validator;

enum Check {
    P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11,
    D1, D2, D3, D4, D5, D6, D7,
    S1, S2, S3, S4, S5, S6, S7, R1, R2, R3, R4, H1, H2, H3, H4, H5, H6, H7, H8, H9, I1, I2;

    boolean rule() {
        return !platform() && !pod();
    }

    boolean platform() {
        return name().startsWith("P");
    }

    boolean pod() {
        return name().startsWith("D");
    }
}
