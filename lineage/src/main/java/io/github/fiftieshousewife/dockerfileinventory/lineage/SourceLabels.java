package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The repository an image says it was built from, by label: OpenShift builds' {@code io.openshift.build.source-location},
 * else OCI's {@code org.opencontainers.image.source}. A label names the repository and lineage's steps say which of its
 * Dockerfiles (the owner's choice, 7 Nov).
 */
final class SourceLabels {

    private static final List<String> LABELS =
            List.of("io.openshift.build.source-location", "org.opencontainers.image.source");

    private SourceLabels() {
    }

    /** {@code org/name}, from a URL on any Git host; a path inside the repository ({@code /tree/main/...}) is dropped. */
    static Optional<String> repository(Map<String, String> labels) {
        return LABELS.stream().map(labels::get).filter(Objects::nonNull).findFirst().flatMap(SourceLabels::fromUrl);
    }

    private static Optional<String> fromUrl(String url) {
        try {
            String path = new URI(url.strip()).getPath();
            String[] segments = path == null ? new String[0] : path.replaceFirst("^/", "").split("/");
            return segments.length < 2 || segments[0].isEmpty() ? Optional.empty()
                    : Optional.of(segments[0] + "/" + segments[1].replaceFirst("\\.git$", ""));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
