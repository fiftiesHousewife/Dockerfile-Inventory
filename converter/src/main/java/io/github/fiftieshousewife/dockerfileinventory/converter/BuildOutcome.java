package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.List;

enum BuildOutcome {
    BUILT,
    MISSING_SOURCE,
    TIMED_OUT,
    FAILED;

    // 124 is coreutils timeout's exit status; Buildah reports a COPY or ADD source missing from the build context,
    // mounted at /src, this way.
    static BuildOutcome of(int exitStatus, String log) {
        if (exitStatus == 0) {
            return BUILT;
        }
        if (exitStatus == 124) {
            return TIMED_OUT;
        }
        return log.contains("checking on sources under \"/src/") ? MISSING_SOURCE : FAILED;
    }

    static final List<String> FETCH_FAILURES = List.of("TLS:", "Hash Sum mismatch",
            "Could not resolve", "Temporary failure resolving", "Connection timed out", "Connection reset",
            "i/o timeout", "TLS handshake timeout", "Curl error (", "curl: (6)", "curl: (7)", "curl: (28)",
            "curl: (35)", "curl: (56)");

    static boolean fetchFailed(String log) {
        return FETCH_FAILURES.stream().anyMatch(log::contains);
    }
}
