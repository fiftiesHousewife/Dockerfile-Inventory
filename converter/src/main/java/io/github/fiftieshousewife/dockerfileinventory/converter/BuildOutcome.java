package io.github.fiftieshousewife.dockerfileinventory.converter;

enum BuildOutcome {
    BUILT,
    MISSING_SOURCE,
    TIMED_OUT,
    FAILED;

    // 124 is coreutils timeout's exit status; Buildah reports a COPY or ADD source it cannot find this way.
    static BuildOutcome of(int exitStatus, String log) {
        if (exitStatus == 0) {
            return BUILT;
        }
        if (exitStatus == 124) {
            return TIMED_OUT;
        }
        return log.contains("checking on sources under") ? MISSING_SOURCE : FAILED;
    }
}
