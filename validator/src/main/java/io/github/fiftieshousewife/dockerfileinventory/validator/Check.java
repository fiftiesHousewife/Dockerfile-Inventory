package io.github.fiftieshousewife.dockerfileinventory.validator;

// The platform checks, in the order of the plan's table (docs/plans/openshift-validator.md), then the rules whose
// findings the validator reads (the rules module's IDs).
enum Check {
    P1, P2, P3, P4, P5, P6, P7, P8, P9, P10,
    S1, S2, S3, S4, S5, S6, R1, R2, R3, H1, H2, H3, H4, H5, H6, H7, I1, I2;

    boolean rule() {
        return !name().startsWith("P");
    }
}
